Best practice in batch deletion scripts
Use .setCount(BATCH_SIZE) → Easier to change, safer for loops.
Optionally .setStart(offset) → If you want paginated iteration instead of repeated “last batch until empty”.