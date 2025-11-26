**Hybris Event System – Key Points**
Used for communication between modules/services without direct dependency.
Handles asynchronous tasks (e.g., send email after order).
Supports cluster-wide events across multiple nodes.
Makes code modular, flexible, and easy to extend.

**Main Parts**
Event → class extending AbstractEvent (e.g., OrderPlacedEvent).
EventService → publishes the event.
EventListener → reacts when the event is fired.

**Flow**
Business logic publishes event → eventService.publishEvent(new OrderPlacedEvent(order));
Event runs asynchronously → listeners execute in background.


Hybris has two main base classes for process actions:
Base class	                                                    Return type	
AbstractAction<T extends BusinessProcessModel>	                defines an action that can perform logic and optionally trigger transitions (e.g., “OK”, “NOK”) but doesn’t return directly
AbstractSimpleDecisionAction<T extends BusinessProcessModel>	a simpler subclass where you just implement logic and return a Transition value directly

Execute simple process step and decide next node	✅ AbstractSimpleDecisionAction
Just perform logic (no transition decision)	        AbstractProceduralAction
Trigger logic asynchronously via event	            AbstractAction / custom event


business process :
The Business Process concept in SAP Commerce (Hybris) is one of the core mechanisms for orchestrating long‑running or multi‑step operations, such as an order fulfillment workflow, approval processes, or data-import flows.
A Business Process is an engine‑driven workflow that consists of a sequence of actions, decisions, and end nodes.

You model it (usually in XML) and it runs in the Hybris Process Engine.
A configurable, persistent state machine that guides how certain business activities unfold step‑by‑step

