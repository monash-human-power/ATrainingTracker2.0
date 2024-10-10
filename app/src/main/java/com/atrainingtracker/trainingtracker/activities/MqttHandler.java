package com.atrainingtracker.trainingtracker.activities;

import org.eclipse.paho.client.mqttv3.MqttCallback;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttHandler implements MqttCallback {

    private MqttClient client;
    private String brokerUrl;
    private String clientId;

    public MqttHandler(String brokerUrl, String clientId) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
    }

    public void connect() {
        try {
            // Set up the persistence layer
            MemoryPersistence persistence = new MemoryPersistence();

            // Initialize the MQTT client
            client = new MqttClient(brokerUrl, clientId, persistence);

            // Set up the connection options
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            client.setCallback(this);
            // Connect to the broker
            client.connect(connectOptions);
            System.out.println("Connected to broker: " + brokerUrl);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("Connection lost! Attempting to reconnect...");
        cause.printStackTrace();
        // Reconnection logic
        while (!client.isConnected()) {
            try {
                Thread.sleep(5000); // Wait for 5 seconds before trying to reconnect
                connect(); // Try reconnecting
            } catch (InterruptedException e) {
                System.out.println("Reconnection attempt failed. Trying again...");
                e.printStackTrace();
            }
        }
        System.out.println("Reconnected successfully!");
    }
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        System.out.println("Message received. Topic: " + topic + " Message: " + new String(message.getPayload()));
    }
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        System.out.println("Delivery complete!");
    }

    public void publish(String topic, String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            client.subscribe(topic);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}