**Exception :** 
“Keep the program running?” → try/catch inside the loop
“Stop everything on error?” → try/catch outside the loop

you should never wrap hundreds or thousands of lines of code in one big try-catch.
Always handle exceptions close to where they occur.

