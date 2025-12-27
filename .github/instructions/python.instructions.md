---
applyTo: '**/*.py'
---
## Guidelines for PYTHON

## FASTAPI
- Use Pydantic models for request and response validation with strict type checking and custom validators
- Implement dependency injection for services and database sessions to improve testability and resource management
- Use async endpoints for I/O-bound operations to improve throughput for {{high_load_endpoints}}
- Leverage FastAPI's background tasks for non-critical operations that don't need to block the response
- Implement proper exception handling with HTTPException and custom exception handlers for {{error_scenarios}}
- Use path operation decorators consistently with appropriate HTTP methods (GET for retrieval, POST for creation, etc.)
