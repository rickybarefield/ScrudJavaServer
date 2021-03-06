package com.appagility.j2ee.websocket.dispatcher.it;

import org.glassfish.tyrus.client.ClientManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TestClientEndpoint extends Endpoint
{
    protected static String OPERATION = "operation";

    private AtomicLong idBase = new AtomicLong();

    private CountDownLatch openLatch = new CountDownLatch(1);
    private CountDownLatch messagesLatch;
    private List<String> messagesReceived = new ArrayList<>();

    private static final long TIMEOUT = 2000;
    private static final long NEGATIVE_ASSERTION_TIMEOUT = 1000;

    private Session session;
    private String lastClientId;

    @Before
    public void connect() throws InterruptedException, URISyntaxException, DeploymentException
    {
        ClientManager client = ClientManager.createClient();
        session = client.connectToServer(this, new URI("ws://localhost:8080/scrud-java-server-integration-test-0.0.1-SNAPSHOT/websocket"));
        boolean started = openLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);

        if(!started) {

            throw new AssertionError("Timeout waiting to establish ws connection");
        }
    }

    @After
    public void disconnect() throws IOException
    {
        session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Test finishing"));
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {

        openLatch.countDown();

        session.addMessageHandler(new MessageHandler.Whole<String>()
        {
            @Override
            public void onMessage(String message)
            {
                synchronized (this) {
                    if(messagesLatch != null && messagesLatch.getCount() > 0) {

                        messagesReceived.add(message);
                        messagesLatch.countDown();
                    }
                }
            }
        });

        System.out.println("onOpen");
    }

    @Override
    public void onClose(Session session, CloseReason closeReason)
    {
        System.out.println("onClose");
    }

    protected void sendMessage(String message) throws IOException
    {
        session.getBasicRemote().sendText(message);
    }

    protected List<String> assertMessagesReceived(String failureMessage) throws InterruptedException
    {
        boolean allMessagesReceived = messagesLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        Assert.assertTrue(failureMessage, allMessagesReceived);
        return messagesReceived;
    }

    protected synchronized void assertNoMessagesReceived(String failureMessage) throws InterruptedException {

        boolean messageReceived = messagesLatch.await(NEGATIVE_ASSERTION_TIMEOUT, TimeUnit.MILLISECONDS);
        Assert.assertFalse(failureMessage, messageReceived);
    }

    protected synchronized void expectMessages(int numberExpected) {

        int numberExpectedOrOneForZero = numberExpected == 0 ? 1 : numberExpected;
        messagesLatch = new CountDownLatch(numberExpectedOrOneForZero);
        messagesReceived = new ArrayList<>();
    }

    public String subscribeExpectingSuccess() throws IOException, InterruptedException
    {
        return sendMessageReplacingClientIdAndExpectSubscriptionSuccess("{message-type: 'subscribe', resource-type: 'Item', client-id: 'CLIENT_ID'}");
    }

    public String subscribeExpectingSuccess(String resourceId) throws IOException, InterruptedException
    {
        return sendMessageReplacingClientIdAndExpectSubscriptionSuccess("{message-type: 'subscribe', resource-type: 'Item', client-id: 'CLIENT_ID', resource-id: '" + resourceId + "'}");
    }

    private String sendMessageReplacingClientIdAndExpectSubscriptionSuccess(String message) throws InterruptedException, IOException
    {
        expectMessages(1);
        String clientId = createClientId();
        sendMessage(message.replace("CLIENT_ID", clientId));
        String response = assertMessagesReceived("No response for subscribe").get(0);
        Assert.assertTrue("Message was received for subscribe but was not a subscription-success, was instead " + response, response.contains("subscription-success"));
        return response;
    }

    public void unsubscribe(String clientId) throws IOException, InterruptedException
    {
        expectMessages(0);
        sendMessage("{message-type: 'unsubscribe', client-id: '" + clientId + "'}");
        assertNoMessagesReceived("No message should be receieved when unsubscribing");
    }

    private String createClientId()
    {
        lastClientId = "myId-" + idBase.incrementAndGet();
        return lastClientId;
    }

    protected String getLastClientId()
    {
        return lastClientId;
    }

    public String createExpectingSuccess(String itemName) throws IOException, InterruptedException
    {
        expectMessages(1);
        String clientId = createClientId();
        sendMessage("{message-type: 'create', client-id: '" + clientId + "', resource-type: 'Item', resource: {name: '" + itemName  + "'}}");
        String response = assertMessagesReceived("No response for create").get(0);
        Assert.assertTrue("Message was received for creation but was not a create-success, was instead " + response, response.contains("create-success"));
        return response;
    }

    public String updateExpectingSuccess(String resourceId, String newItemName) throws IOException, InterruptedException
    {
        expectMessages(1);
        String clientId = createClientId();
        sendMessage("{message-type: 'update', resource-id: '"+ resourceId + "', client-id: '" + clientId + "', resource-type: 'Item', resource: {name: '" + newItemName + "'}}");
        String response = assertMessagesReceived("No response for update").get(0);
        Assert.assertTrue("Message was received for update but was not a update-success, was instead " + response, response.contains("update-success"));
        return response;
    }
}
