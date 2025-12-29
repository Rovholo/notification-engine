package com.bitkulcha.notification_engine.config;

import com.bitkulcha.notification_engine.dto.BrokerDto;
import com.bitkulcha.notification_engine.dto.BrokerDtoImmtbl;
import com.bitkulcha.notification_engine.service.FirebaseService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableIntegration
public class MqttConfig {
    private static final String CLIENT_ID_SUB = "springBootSubClient";
    private static final String CLIENT_ID_PUB = "springBootPubClient";

    private final FirebaseService firebaseService;

    public MqttConfig(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        BrokerDto brokerDto = firebaseService.getAllBrokers().stream()
                .findFirst()
                .orElse(BrokerDtoImmtbl.builder()
                        .server("localhost")
                        .username("username")
                        .password("password")
                        .isSecure(false)
                        .build());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {"ssl://" + brokerDto.getServer() + ":8883"});
        options.setUserName(brokerDto.getUsername());
        options.setPassword(brokerDto.getPassword().toCharArray());
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
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            firebaseService.handleMessage(topic, message.getPayload());
        };
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(CLIENT_ID_SUB, mqttClientFactory(), "#");
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
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(CLIENT_ID_PUB, mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic("topic/test");
        return handler;
    }

}
