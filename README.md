# Order Fulfilment Workflow

## 1. Project Overview

Order Fulfilment Workflow is a full-stack application built with Java 21, Spring Boot, Camunda Platform 7, and Angular 18. It demonstrates business process automation using BPMN 2.0, REST API integration, external task workers, manual credit approval, timer and message boundary events, parallel execution, and notification routing.

The application separates API handling, business logic, workflow orchestration, and frontend responsibilities into distinct modules.

## 2. Technology Stack

| Component | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.6 |
| Workflow Engine | Camunda Platform 7.24.0 |
| Workflow Definition | BPMN 2.0 |
| External Task Client | Camunda External Task Client 7.24.0 |
| Frontend | Angular 18, TypeScript, HTML, CSS |
| Build Tool | Gradle |
| API Documentation | Swagger UI / OpenAPI |
| Testing | JUnit and Spring Boot testing support |
| Package Manager | npm |

## 3. Architecture

The solution consists of a modular Spring Boot backend, a separate Camunda process engine, and an Angular frontend.

### Components

- **API module:** Exposes REST endpoints for order creation and retrieval.
- **Service module:** Contains business logic, persistence-related components, and Camunda external task workers.
- **Application module:** Provides the Spring Boot application entry point and configuration.
- **Camunda engine:** Executes the deployed BPMN process, manages process instances, and coordinates workflow activities.
- **Angular frontend:** Supports order submission, order status tracking, credit reviews, and priority updates.

### Request Flow

1. A user submits an order through the Angular application.
2. The Spring Boot API validates the order request and saves the order.
3. The backend starts the Camunda process through the Camunda REST API.
4. Camunda executes the BPMN workflow and coordinates the process activities.
5. External task workers perform the configured business operations.
6. The frontend retrieves order information and displays its status.

## 4. Project Structure

```text
order-fulfilment/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   └── resources/
│       │       └── order-fulfilment.bpmn
│       └── test/
├── api/
│   └── src/
├── service/
│   └── src/
├── frontend/
│   └── src/
├── buildSrc/
├── gradle/
├── gradlew
├── gradlew.bat
├── settings.gradle
├── build.gradle
├── README.md
└── AI-USAGE.md
```

## 5. BPMN Workflow

The workflow definition is located at:

`app/src/main/resources/order-fulfilment.bpmn`

### 5.1 Order Validation

The workflow validates the order and customer before proceeding to the credit decision. Invalid customers follow the rejection path, and the rejection reason is recorded.

### 5.2 Credit Decision

The workflow uses the following business rules:

- Orders with an amount less than or equal to €1,000 are automatically approved.
- Orders above €1,000 require manual review by a credit officer.
- The credit officer can approve or reject a pending order.

### 5.3 Automatic Cancellation

A boundary timer attached to the credit-review activity automatically cancels an order when approval is not completed within the configured duration.

The current BPMN source uses:

`PT10M`

This represents a ten-minute timeout.

For demonstration purposes, the timeout can be changed to `PT1M` for one minute or `PT30S` for thirty seconds.

After changing the BPMN duration:

1. Save the BPMN file.
2. Deploy the updated BPMN definition to Camunda.
3. Start a new high-value order.
4. Leave the order pending without approving or rejecting it.
5. Wait for the timeout and refresh the order status.

Redeployment does not change the timer on process instances that are already running. Use a new order to test the updated duration.

### 5.4 Priority Update

A non-interrupting message boundary event handles priority updates during credit review. It triggers SLA logging without interrupting the main credit-review activity.

### 5.5 Parallel Fulfilment

After approval, inventory reservation and invoice generation execute in parallel.

### 5.6 Notification Routing

An inclusive gateway routes the workflow according to the notification preference:

- `EMAIL`
- `SMS`
- `BOTH`
- `NONE`

The workflow executes the corresponding notification activities before reaching the appropriate completion path.

## 6. Prerequisites

Install the following tools:

- JDK 21
- Node.js compatible with Angular 18
- npm
- Git
- A running Camunda Platform 7.24.0 engine with its REST API enabled

The project includes a Gradle wrapper, so a separate Gradle installation is not required.

## 7. Setup and Execution

### Step 1: Start Camunda

Start the standalone Camunda engine and verify its REST API:

`http://localhost:8080/engine-rest/engine`

The running engine version can be verified using:

`http://localhost:8080/engine-rest/version`

The expected version is `7.24.0`.

The backend communicates with Camunda through:

`http://localhost:8080/engine-rest`

Ensure the engine is running before starting the backend.

### Step 2: Deploy the BPMN Process

Run the following command from the project root in PowerShell:

```powershell
curl.exe -X POST `
  -F "deployment-name=order-fulfilment" `
  -F "order-fulfilment.bpmn=@app/src/main/resources/order-fulfilment.bpmn" `
  http://localhost:8080/engine-rest/deployment/create
```

Verify that the response contains a deployment ID and the deployed process definition.

### Step 3: Build and Test the Backend

From the project root:

```powershell
.\gradlew.bat clean test build
```

### Step 4: Start the Spring Boot Application

```powershell
.\gradlew.bat :app:bootRun
```

The backend is expected to run on port `8081`.

### Step 5: Start the Angular Frontend

Open a separate terminal:

```powershell
cd frontend
npm install
npm start
```

Open the application:

`http://localhost:4200`

## 8. REST API

The following endpoints are implemented in `OrderController`.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/orders` | Create an order and start its workflow |
| GET | `/api/orders` | Retrieve all orders |
| GET | `/api/orders/{orderId}` | Retrieve an order by ID |
| GET | `/api/orders/{orderId}/status` | Retrieve order status and rejection details |

### 8.1 Create Order

**Endpoint**

`POST http://localhost:8081/api/orders`

**Example request**

```json
{
  "orderId": "ORD-1001",
  "customerId": "CUST-101",
  "amount": 500,
  "notificationPreference": "EMAIL"
}
```

### 8.2 Request Validation

The order creation endpoint validates the following fields:

- `orderId`: Required, non-empty string.
- `customerId`: Required, non-empty string.
- `amount`: Required numeric value greater than zero.
- `notificationPreference`: Optional in the current controller implementation; defaults to `BOTH` when absent or not provided as a string.

Allowed notification preferences are `EMAIL`, `SMS`, `BOTH`, and `NONE`. Values are trimmed and converted to uppercase before validation.

Duplicate order IDs return HTTP `409 Conflict`. Invalid required fields, invalid amounts, or unsupported notification preferences return HTTP `400 Bad Request`.

The current `OrderController` uses `Map<String, Object>` to read the request body. `OrderRequest.java` defines a separate DTO with `preferredNotification`, but that DTO is not used by the current order creation method. The controller's actual field name is `notificationPreference`.

### 8.3 Example Response

A successful request returns order information similar to the following:

```json
{
  "orderId": "ORD-1001",
  "processInstanceId": "example-process-instance-id",
  "status": "PROCESS_STARTED"
}
```

The process instance ID is generated by Camunda. The final status depends on workflow execution.

### 8.4 API Documentation

Swagger UI is available at:

`http://localhost:8081/swagger-ui/index.html`

Use the running API documentation to inspect the operations and their schemas.

## 9. External Task Workers

The service module contains the workflow worker implementation:

`service/src/main/java/com/example/orderfulfilment/service/worker/OrderWorkflowWorkers.java`

The workers execute business operations associated with the BPMN process, including validation, fulfilment, SLA logging, and notification-related activities.

### Notification Worker Behaviour

The current notification workers demonstrate workflow execution by setting process variables:

- Email activity sets `emailNotificationSent` to `true`.
- SMS activity sets `smsNotificationSent` to `true`.

These workers do not currently demonstrate actual email or SMS delivery through SMTP or an external messaging provider. The variables indicate that the corresponding workflow activities executed; they do not confirm that a message was delivered to a real inbox or phone.

## 10. Testing

### 10.1 Backend Tests

Run the backend test suite:

```powershell
.\gradlew.bat clean test
```

Build the backend modules:

```powershell
.\gradlew.bat clean test build
```

### 10.2 Frontend Build

From the `frontend` directory:

```powershell
npm install
npm run build
```

### 10.3 End-to-End Scenarios

The following scenarios have been used to validate the workflow:

| Scenario | Expected behaviour |
|---|---|
| Valid low-value order | Automatic approval and fulfilment |
| High-value order | Pending credit review |
| Approved high-value order | Fulfilment after manual approval |
| Rejected order | Rejection path |
| Invalid customer | Rejection with a recorded reason |
| Credit review timeout | Automatic cancellation |
| Priority update | Message event and SLA logging |
| Email preference | Email notification activity executes |
| SMS preference | SMS notification activity executes |
| Both preferences | Email and SMS notification activities execute |
| No notifications | Notification activities are skipped |

Notification activity execution should be distinguished from actual message delivery.

## 11. Camunda Troubleshooting

The following Camunda REST endpoints are useful when diagnosing workflow execution.

| Endpoint | Purpose |
|---|---|
| `/engine-rest/engine` | List available process engines |
| `/engine-rest/version` | Verify the running engine version |
| `/engine-rest/deployment/create` | Deploy a BPMN definition |
| `/engine-rest/process-definition/key/orderFulfilmentProcess/start` | Start a process instance |
| `/engine-rest/history/process-instance` | Inspect historical process instances |
| `/engine-rest/history/activity-instance` | Inspect executed activities |
| `/engine-rest/history/variable-instance` | Inspect historical process variables |

When debugging a process, use the process instance ID to inspect its history, executed activities, and variables. Historical data availability depends on Camunda history configuration.

## 12. Implementation Considerations

- Camunda runs independently from the Spring Boot application.
- BPMN defines workflow routing, event handling, and process coordination.
- External task workers implement the business operations assigned to them.
- The current notification workers record activity execution rather than delivering real messages.
- Production integrations for inventory, invoicing, email, and SMS can be added through the corresponding worker implementations.
- The process definition must be redeployed after BPMN changes.
- Existing process instances continue with the configuration they started with.

## 13. AI Usage

Refer to `AI-USAGE.md` for information about AI-assisted development and verification.

## 14. Submission Checklist

- [ ] Confirm the BPMN timeout matches the intended submission configuration.
- [ ] Deploy the final BPMN definition successfully.
- [ ] Verify the running Camunda engine version.
- [ ] Run `.\gradlew.bat clean test build`.
- [ ] Build the Angular frontend successfully.
- [ ] Verify order creation and retrieval through the API.
- [ ] Verify automatic approval, manual approval, rejection, timeout cancellation, and priority update.
- [ ] Verify notification routing and clearly document the current demonstration-only workers.
- [ ] Include `README.md` and `AI-USAGE.md`.
- [ ] Exclude `node_modules`, generated build output, local databases, and unnecessary IDE files from the submission archive.

---

**Backend:** Java 21 and Spring Boot  
**Workflow Engine:** Camunda Platform 7.24.0  
**Frontend:** Angular 18  
**Build System:** Gradle
