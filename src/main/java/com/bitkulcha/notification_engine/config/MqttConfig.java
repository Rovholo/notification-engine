package com.bitkulcha.notification_engine.config;

import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.exception.BrokerNotFoundException;
import com.bitkulcha.notification_engine.service.BrokerService;
import com.bitkulcha.notification_engine.service.DeviceMessageService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.Arrays;

@Slf4j
@Configuration
@EnableIntegration
public class MqttConfig {
    private static final String CLIENT_ID_SUB = "springBootSubClient";
    private static final String CLIENT_ID_PUB = "springBootPubClient";

    private final Environment environment;
    private final BrokerService brokerService;
    private final DeviceMessageService deviceMessageService;

    public MqttConfig(Environment environment, BrokerService brokerService, DeviceMessageService deviceMessageService) {
        this.environment = environment;
        this.brokerService = brokerService;
        this.deviceMessageService = deviceMessageService;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        String server = "localhost";
        String username = "username";
        String password = "password";
        try {
            BrokerModel broker = brokerService.getDefaultBroker();
            server = broker.getServer();
            username = broker.getUsername();
            password = broker.getPassword();
        } catch (BrokerNotFoundException e) {
            // Keeps the app starting without a broker row (e.g. locally); MQTT just won't connect.
            log.warn("No broker found for mqtt.broker-name, using the localhost placeholder: {}", e.getMessage());
        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {"ssl://" + server + ":8883"});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(60);

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    /// Incoming config
    @Bean(name = "mqttInboundChannel")
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler inboundHandler() {
        return message -> deviceMessageService.handleMessage(
                (String) message.getHeaders().get("mqtt_receivedTopic"),
                message.getPayload());
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                CLIENT_ID_SUB + Arrays.toString(environment.getActiveProfiles()),
                mqttClientFactory(), "#");
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    /// Outgoing config
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler outbound() {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                CLIENT_ID_PUB + Arrays.toString(environment.getActiveProfiles()),
                mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic("topic/test");
        return handler;
    }

}
