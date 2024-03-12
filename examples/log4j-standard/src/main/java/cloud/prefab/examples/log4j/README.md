## Log4j Filter example

This example demonstrates usage of the log4j filter along with global and thread-local context for targeted log level configuration

### Instructions

Set the `PREFAB_API_KEY` environment variable when running. 

`cloud.prefab.example.log4j.Main` should quickly appear in the logging configuration screen at prefab 

![Screenshot 2024-03-12 at 16.20.33.png](../../../../../../../images/Screenshot%202024-03-12%20at%2016.20.33.png)

Information like the event source (from the thread-local "event" context can be used to target the logging like this)

![Screenshot 2024-03-12 at 16.20.50.png](../../../../../../../images/Screenshot%202024-03-12%20at%2016.20.50.png)