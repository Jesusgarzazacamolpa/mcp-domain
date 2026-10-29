# mcp-domain - Servidor MCP de dominio

Servicio Java 21 + Spring Boot que expone las operaciones de negocio de la compra de
vehículos como herramientas MCP (Model Context Protocol) sobre HTTP. El orquestador y su
agente LLM lo consumen en `http://localhost:9001/mcp`; el servicio no guarda datos propios:
valida las llamadas, aplica idempotencia y delega en `vehicle-domain-service`.

## Qué hace

- Publica cuatro tools: consultar vehículo, registrar compra, configurar pago y solicitar
  aprobación de financiamiento.
- Publica el recurso `floworchestrator://domain/payment-defaults` con los valores por defecto
  de pago.
- Describe cada tool con un esquema JSON y anotaciones (`readOnlyHint`, `idempotentHint`,
  `openWorldHint`) para que el modelo sepa cuándo puede reintentar.
- Devuelve los errores como datos estructurados (`status=ERROR`, `error_code`, `how_to_fix`,
  `retryable`) en lugar de lanzar excepciones al protocolo.
- Deduplica escrituras con una caché en memoria para que un reintento no cree una compra o un
  pago duplicado.

## Arquitectura

```mermaid
flowchart LR
    O[Orchestrator<br/>agente LLM] -->|MCP Streamable HTTP<br/>POST /mcp| M[mcp-domain<br/>:9001]
    M -->|GET /vehicles/{id}| V[vehicle-domain-service<br/>:8083]
    M -->|POST /purchase-orders| V
    M -->|POST /payment-methods| V
```

El orquestador no llama directamente a `vehicle-domain-service`: descubre las tools por MCP y
el agente decide cuándo invocarlas. `mcp-domain` es la única capa que conoce los endpoints REST
del dominio y traduce sus fallos a mensajes que el modelo puede corregir.

El transporte se registra en [McpServerConfiguration.java](src/main/java/com/floworchestrator/mcpdomain/config/McpServerConfiguration.java):
`HttpServletStreamableServerTransportProvider` con `mcpEndpoint("/mcp")`, servlet en `/mcp`,
`serverInfo` `mcp-domain` versión `1.0.0` y `validateToolInputs(false)`. La validación real la
hacen [ToolArguments.java](src/main/java/com/floworchestrator/mcpdomain/tools/ToolArguments.java)
y las reglas del dominio.

## Inicio rápido

Requisitos: JDK 21+, Maven 3.9+ y `vehicle-domain-service` accesible (por defecto
`http://localhost:8083`).

Con Docker Compose, desde la raíz del repositorio:

```bash
docker compose up -d vehicle-domain-service mcp-domain
```

En local:

```bash
mvn -o package -DskipTests

# La URL del dominio se puede sobreescribir por entorno
export AUTO_DATABASE_URL=http://localhost:8083
"$JAVA_HOME/bin/java" -jar target/mcp-domain-0.1.0.jar
```

En Windows (cmd):

```bat
set AUTO_DATABASE_URL=http://localhost:8083
"%JAVA_HOME%\bin\java" -jar target\mcp-domain-0.1.0.jar
```

El servicio no expone `/actuator/health`. Para confirmar que arrancó, busca en el log:

```text
[MCP_SERVER_01] Servidor MCP inicializado | tools=[get_vehicle_information, register_vehicle_purchase, set_payment_method, request_financing_approval], resource=floworchestrator://domain/payment-defaults
```

Un cliente MCP debe conectarse al endpoint `http://localhost:9001/mcp`. La prueba
`McpServerIntegrationTest` es la referencia ejecutable de un cliente Streamable HTTP.

## Herramientas

| Tool | Obligatorios | Efecto | Idempotente | Solo lectura |
| --- | --- | --- | --- | --- |
| `get_vehicle_information` | `vehicle_id` | Consulta el catálogo | Sí | Sí |
| `register_vehicle_purchase` | `customer_id`, `vehicle_id`, `sales_agent_id` | Crea la orden de compra | Sí | No |
| `set_payment_method` | `purchase_order_id` | Configura el pago | Sí | No |
| `request_financing_approval` | `purchase_order_id` | Solicita o confirma la aprobación humana | Sí | No |

El recurso `floworchestrator://domain/payment-defaults` publica los valores que
`set_payment_method` aplica cuando se omiten argumentos opcionales:

```json
{
  "payment_type": "FINANCING",
  "financial_institution": "BBVA",
  "down_payment_amount": 15000,
  "currency": "MXN",
  "notes": "set_payment_method aplica estos valores cuando se omite el argumento opcional correspondiente."
}
```

La referencia completa de esquemas, ejemplos, respuestas y códigos de error está en
[docs/01-herramientas-mcp.md](docs/01-herramientas-mcp.md).

## Flujo de negocio

Los flujos del orquestador declaran el orden y las dependencias en
[flow-definitions.yml](../orchestrator/src/main/resources/flow-definitions.yml).

Compra directa (`compra-vehiculo`):

```text
get_vehicle_information -> register_vehicle_purchase -> set_payment_method -> send_purchase_status_email
```

Compra con financiamiento (`compra-financiamiento`):

```text
get_vehicle_information -> register_vehicle_purchase -> request_financing_approval -> send_purchase_status_email
```

En el flujo con financiamiento, la primera llamada a `request_financing_approval` (sin
`decision`) devuelve `status=PENDING_HUMAN_APPROVAL` y `requiresIntervention=true`. El
orquestador pausa el proceso y el Control Center crea una intervención. Cuando un operador
aprueba, el flujo se reanuda y el agente vuelve a llamar a la tool con `decision=APPROVED`,
que responde `status=FINANCING_APPROVED`. Si el operador rechaza, el proceso se cancela y la
tool no se vuelve a invocar.

## Idempotencia

`register_vehicle_purchase` y `set_payment_method` calculan una clave determinista y guardan
el resultado en una caché Caffeine (máximo 1000 entradas, retención de 2 horas) implementada
en [IdempotencyStore.java](src/main/java/com/floworchestrator/mcpdomain/service/IdempotencyStore.java).
Un segundo intento con los mismos argumentos devuelve la respuesta original con
`idempotent_replay=true` en lugar de repetir el efecto en el dominio.

- La compra usa `idempotency_key` si se envía; si se omite, la clave se deriva de
  `customer_id`, `vehicle_id` y `sales_agent_id`.
- El `purchase_order_id` también es determinista (`po-` + UUID derivado), así que el dominio
  reconoce la orden aunque la caché local se haya perdido.
- Si `vehicle-domain-service` responde `409`, `mcp-domain` lo absorbe y marca
  `reused_existing_order=true`. En el dominio actual un `409` indica que el identificador ya
  pertenece a otro vehículo o cliente; el replay normal de una compra idéntica responde 200.
- El pago se deduplica por la combinación resuelta de orden, tipo de pago, institución y
  monto; cambiar cualquier valor genera una configuración nueva.

La caché es por instancia y en memoria: un reinicio la vacía. La idempotencia frente a
duplicados persistentes depende además de la clave determinista y del manejo del `409` del
dominio.

## Contrato de errores

Cuando una tool falla, la respuesta MCP llega con `isError=true` y un cuerpo JSON:

```json
{
  "status": "ERROR",
  "tool": "register_vehicle_purchase",
  "error_code": "MISSING_ARGUMENT",
  "message": "Falta el argumento obligatorio 'customer_id' para la herramienta 'register_vehicle_purchase'.",
  "how_to_fix": "Solicita al usuario el identificador del cliente y vuelve a llamar a 'register_vehicle_purchase' con el campo 'customer_id' establecido. No inventes un valor.",
  "field": "customer_id",
  "retryable": false
}
```

| Código | Causa | Reintentable |
| --- | --- | --- |
| `MISSING_ARGUMENT` | Falta un campo obligatorio o está vacío | No |
| `INVALID_ARGUMENT` | Tipo o formato inválido; el dominio respondió 4xx | No |
| `VEHICLE_NOT_FOUND` | El catálogo no devolvió el vehículo | No |
| `RESOURCE_NOT_FOUND` | El dominio respondió 404 (por ejemplo, orden inexistente) | No |
| `ALREADY_EXISTS` | El dominio respondió 409 fuera del alta de compra | No |
| `DEPENDENCY_UNAVAILABLE` | El dominio respondió 5xx | Sí, una vez |
| `INTERNAL_ERROR` | Fallo inesperado de la tool | No |
| `SERIALIZATION_ERROR` | No se pudo serializar la respuesta | No |

La conversión de excepciones vive en
[ToolErrors.java](src/main/java/com/floworchestrator/mcpdomain/tools/ToolErrors.java) y se aplica
en [ToolSupport.java](src/main/java/com/floworchestrator/mcpdomain/tools/ToolSupport.java), de
modo que ninguna excepción escapa al servidor MCP.

## Configuración

| Propiedad | Variable de entorno | Por defecto | Descripción |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `9001` | Puerto HTTP del servidor MCP |
| `services.auto-database.base-url` | `AUTO_DATABASE_URL` | `http://localhost:8083` | URL base de `vehicle-domain-service` usada por los clientes Feign |
| `logging.level.com.floworchestrator.mcpdomain` | - | `DEBUG` | Nivel general del paquete |
| `logging.level.com.floworchestrator.mcpdomain.tools` | - | `INFO` | Nivel de las tools |

El archivo base es [application.yml](src/main/resources/application.yml). El apagado es
`graceful`.

En Docker Compose, `AUTO_DATABASE_URL` se define como
`http://vehicle-domain-service:8083`. La imagen usa `eclipse-temurin:21-jre` y espera el jar
`target/mcp-domain-0.1.0.jar`, por lo que hay que compilar antes de construir la imagen:

```bash
mvn -o package -DskipTests
docker build -t mcp-domain services/mcp-domain
```

## Integración con el orquestador

El orquestador resuelve la URL del servidor desde `orchestrator.mcp-servers` y conecta con el
endpoint `/mcp`:

```yaml
orchestrator:
  mcp-servers:
    mcp-domain: http://localhost:9001
```

En Docker Compose la variable es `SERVICES_MCP_DOMAIN_BASE_URL=http://mcp-domain:9001`. Los
nombres de las tools deben coincidir exactamente con los declarados en `flow-definitions.yml`.

## Pruebas

```bash
# Pruebas unitarias y de integración del servicio
mvn -o test

# Pruebas + reporte JaCoCo + gate de cobertura de líneas >= 80 %
mvn -o verify
```

Si Maven no tiene las dependencias en caché, omite `-o`. En Windows, si la descarga falla por
certificados, ejecuta con
`MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"`.

`McpServerIntegrationTest` levanta la aplicación en un puerto aleatorio, se conecta con el
cliente MCP oficial, verifica las tools, el recurso y una respuesta de error accionable. No
necesita `vehicle-domain-service` porque no ejecuta llamadas al dominio. Las pruebas unitarias
de las tools y de `VehicleDomainService` usan repositorios simulados.

El análisis estático se publica con SonarQube:

```bash
mvn -o verify sonar:sonar -Dsonar.token="$SONAR_TOKEN"
```

## Solución de problemas

- **`VEHICLE_NOT_FOUND`**: el identificador no existe en el catálogo. Confirma el
  `vehicle_id`; no reintentes con el mismo valor.
- **`DEPENDENCY_UNAVAILABLE`**: `vehicle-domain-service` respondió 5xx o no está disponible.
  Revisa `AUTO_DATABASE_URL` y la salud del dominio antes de reintentar.
- **`RESOURCE_NOT_FOUND` al configurar el pago**: la orden no existe en el dominio. Verifica
  que `register_vehicle_purchase` se haya completado antes.
- **El orquestador no ve las tools**: comprueba que el cliente apunte a la base
  `http://host:9001` con endpoint `/mcp` y que el log muestre `Servidor MCP inicializado`.
- **La respuesta parece de una versión anterior**: el Dockerfile copia el jar ya compilado.
  Vuelve a ejecutar `mvn -o package -DskipTests` antes de reconstruir la imagen.
- **Puerto 9001 ocupado**: cambia `SERVER_PORT` o detén el proceso que lo está usando.

## Límites actuales

- El endpoint MCP no implementa autenticación propia; debe exponerse solo en una red interna
  o detrás de un proxy que la gestione.
- La idempotencia es local a cada instancia y se pierde al reiniciar.
- `request_financing_approval` no consulta ni decide la aprobación: solo informa al orquestador
  que se requiere una intervención. La decisión vive en el Control Center.
