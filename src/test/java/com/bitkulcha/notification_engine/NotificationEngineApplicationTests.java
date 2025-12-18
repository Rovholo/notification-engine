package com.bitkulcha.notification_engine;

import com.bitkulcha.notification_engine.service.FirebaseService;
import com.bitkulcha.notification_engine.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.profiles.active=test")
class NotificationEngineApplicationTests {

	@MockitoBean
	private NotificationService notificationService;

	@MockitoBean
	private FirebaseService firebaseService;

	@Test
	void contextLoads() {
	}

}
