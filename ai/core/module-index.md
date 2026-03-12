# Module Relationships

High-level dependency flow inferred from code structure:

builder → filter  
sqllike → filter execution engine  
chart → builder query outputs  
benchmark → full query execution stack

These relationships indicate:

- `filter` provides the core execution engine
- `builder` and `sqllike` are API entry layers
- `chart` and reporting modules consume query results
- `benchmark` validates execution performance

These relationships should be re-evaluated if large refactors occur.