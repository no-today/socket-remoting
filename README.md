# Java Socket Library: Zero extra dependencies

## Usage example

### setting log level

```java
LogUtil.setLevel(LogUtil.Level.DEBUG);
```

### start the service and client

```java
// Create server and client instance
RemotingServer server = new SocketRemotingServer(PORT);
SocketRemotingClient client = new SocketRemotingClient("127.0.0.1", PORT);

// When starting the server, there will be an infinite loop inside to receive client connections, so it needs to be executed asynchronously.
new Thread(server::start).start();

// Waiting 100 milliseconds is to allow the server to start first
TimeUnit.MILLISECONDS.sleep(100);
new Thread(client::connect).start();
```

### register request handle

```java
// Add a processor to the server, cmd code is 1024, this handler will return the request unchanged, you can also add the processor after the service is started
int serverCmd = 1024;
server.registerProcessor(serverCmd, (ctx, request) -> request.setCode(RemotingSystemCode.SUCCESS));

// The client can also process instructions issued by the server, which is bidirectional.
int clientCmd = 2048;
client.registerProcessor(clientCmd, (ctx, req) -> req.setCode(RemotingSystemCode.SUCCESS));
```

### client send cmd to server

```java
// Build request cmd
RemotingCommand request = RemotingCommand.request(cmd, UUID.randomUUID().toString().getBytes(), Map.of("traceId", UUID.randomUUID().toString()));

// Sync request to server
RemotingCommand response = client.syncRequest(request, 3000);
assertArrayEquals(request.getBody(), response.getBody());
assertEquals(request.getExtFields(), response.getExtFields());

// Async request to server
client.asyncRequest(request, 3000, (res, err) -> {
    if (err != null) {
        // failure
        System.out.println("Async request failure: " + err);
    } else {
        // success
        assertArrayEquals(request.getBody(), res.getBody());
        assertEquals(request.getExtFields(), res.getExtFields());
    }
});

// Oneway request to server, no response required
client.onewayRequest(request, 3000, (req, err) -> {
    if (err != null) {
        // failure
        System.out.println("Async request failure: " + err);
    }
});
```

### server send cmd to client

```java
// Need to specify which connection to send to
String client = server.getChannels().get(0);

// Build request cmd
RemotingCommand request = RemotingCommand.request(cmd, UUID.randomUUID().toString().getBytes(), Map.of("traceId", UUID.randomUUID().toString()));

// Sync request to client
RemotingCommand response = server.syncRequest(client, request, 3000);
assertArrayEquals(request.getBody(), response.getBody());
assertEquals(request.getExtFields(), response.getExtFields());

// Async request to client
server.asyncRequest(client, request, 3000, (res, err) -> {
    if (err != null) {
        // failure
        System.out.println("Async request failure: " + err);
    } else {
        // success
        assertArrayEquals(request.getBody(), res.getBody());
        assertEquals(request.getExtFields(), res.getExtFields());
    }
});

// Oneway request to client, no response required
server.onewayRequest(client, request, 3000, (req, err) -> {
    if (err != null) {
        // failure
        System.out.println("Async request failure: " + err);
    }
});
```

### client automatically reconnects

1. The server is unavailable when the client is initially started.
2. The server is unavailable during communication

```java
// Just use the third construction parameter
SocketRemotingClient client = new SocketRemotingClient("127.0.0.1", PORT, true);
```

[QuickStartTest.java](src/test/java/io/github/no/today/socket/remoting/QuickStartTest.java)