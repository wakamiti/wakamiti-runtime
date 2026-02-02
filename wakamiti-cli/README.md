## wakamiti-cli

El cli será un simple proxy que se encargará de enviar los comandos como texto vía HTTP POST al daemon de Wakamiti.
Allí se creará un job con un id y se lanzará de forma asíncrona. El POST devolverá el resultado de la operación y el id.
Con este id se conectará a la salida del log de dicho job via websocket. Una vez acabe la ejecución, se cerrará el 
websocket.

Pulsando Ctrl+C se enviará una petición de parada de la ejecución de wakamiti.