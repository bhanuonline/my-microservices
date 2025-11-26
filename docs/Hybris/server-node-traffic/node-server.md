Scenario:

    Node 1 → Unresponsive
    Node 2 → Available
    Goal → Replace Node 1 with a new node without affecting the system.

Recommended Process
1. Confirm Node Status
    Check which node is unresponsive (Node 1).
    Make sure Node 2 is fully functional and serving traffic.
2. Remove the Unresponsive Node from Load Balancer
    Do not immediately shut it down.
    Mark Node 1 as offline/disabled in your load balancer.
    This ensures no new traffic goes to Node 1.
3. Stop the Unresponsive Node (Optional)