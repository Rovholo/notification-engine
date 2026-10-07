package com.bitkulcha.notification_engine.config;

import com.bitkulcha.notification_engine.domain.model.BrokerModelImmtbl;
import com.bitkulcha.notification_engine.exception.BrokerNotFoundException;
import com.bitkulcha.notification_engine.service.BrokerService;
import com.bitkulcha.notification_engine.service.DeviceMessageService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttConfigTest {

    @Mock
    private Environment environment;

    @Mock
    private BrokerService brokerService;

    @Mock
    private DeviceMessageService deviceMessageService;

    @InjectMocks
    private MqttConfig mqttConfig;

    @Test
    void mqttClientFactory_connectsToTheDefaultBrokerFromTheDatabase() {
        when(brokerService.getDefaultBroker())
                .thenReturn(BrokerModelImmtbl.builder()
                        .id(UUID.randomUUID())
                        .name("main")
                        .server("broker.example.com")
                        .username("user")
                        .password("secret")
                        .isSecure(true)
                        .build());

        MqttConnectOptions options = connectionOptions();

        assertThat(options.getServerURIs()).containsExactly("ssl://broker.example.com:8883");
        assertThat(options.getUserName()).isEqualTo("user");
        assertThat(options.getPassword()).isEqualTo("secret".toCharArray());
    }

    @Test
    void mqttClientFactory_whenNoDefaultBroker_fallsBackToLocalhostPlaceholder() {
        when(brokerService.getDefaultBroker()).thenThrow(new BrokerNotFoundException("No such broker"));

        MqttConnectOptions options = connectionOptions();

        assertThat(options.getServerURIs()).containsExactly("ssl://localhost:8883");
        assertThat(options.getUserName()).isEqualTo("username");
    }

    @Test
    void inboundHandler_passesMessagesToDeviceMessageService() {
        Message<String> message = MessageBuilder.withPayload("{\"mode\":\"start\"}")
                .setHeader("mqtt_receivedTopic", "home/h/reaction/d")
                .build();

        mqttConfig.inboundHandler().handleMessage(message);

        verify(deviceMessageService).handleMessage("home/h/reaction/d", "{\"mode\":\"start\"}");
    }

    private MqttConnectOptions connectionOptions() {
        return ((DefaultMqttPahoClientFactory) mqttConfig.mqttClientFactory()).getConnectionOptions();
    }
}
