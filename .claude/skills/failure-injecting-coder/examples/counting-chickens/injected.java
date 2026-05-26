// 混入版：「将来 SMS や LINE にも送るかも」を口頭で語って、抽象通知基盤を組む。
// - Notification 値オブジェクト
// - NotificationChannel インタフェース
// - EmailChannel 実装（現時点で唯一の実装）
// - NotificationFactory / NotificationRouter（チャネル選択を将来差し替えられるように）
// - NotificationService（ファサード）

package com.example.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 通知の種別。将来 SMS、LINE、Push を追加できるようにする
enum NotificationChannelType {
    EMAIL, SMS, LINE, PUSH
}

// 抽象的な通知の値オブジェクト
class Notification {
    private final NotificationChannelType channelType;
    private final String recipient;   // EMAIL なら email、SMS なら phone number、LINE なら userId
    private final String subject;     // SMS や LINE では使わないかも
    private final String body;

    public Notification(NotificationChannelType channelType, String recipient, String subject, String body) {
        this.channelType = channelType;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public NotificationChannelType channelType() { return channelType; }
    public String recipient() { return recipient; }
    public String subject() { return subject; }
    public String body() { return body; }
}

// 通知チャネルの抽象
interface NotificationChannel {
    NotificationChannelType supports();
    void send(Notification notification);
}

// 現時点で唯一の実装
@Component
class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final JavaMailSender mailSender;

    EmailNotificationChannel(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public NotificationChannelType supports() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(notification.recipient());
        message.setSubject(notification.subject());
        message.setText(notification.body());
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email: to={}", notification.recipient(), e);
            throw new NotificationException("email send failed", e);
        }
    }
}

// チャネル選択のルータ。将来 SMS/LINE が増えても呼び出し側を変えずに済むようにする
@Component
class NotificationRouter {

    private final Map<NotificationChannelType, NotificationChannel> channels = new HashMap<>();

    @Autowired
    NotificationRouter(List<NotificationChannel> channelList) {
        for (NotificationChannel ch : channelList) {
            channels.put(ch.supports(), ch);
        }
    }

    public NotificationChannel resolve(NotificationChannelType type) {
        NotificationChannel ch = channels.get(type);
        if (ch == null) {
            throw new NotificationException("No channel registered for: " + type, null);
        }
        return ch;
    }
}

// ファサード。呼び出し側はこの Service だけ知っていればいい
@Service
public class NotificationService {

    private final NotificationRouter router;

    public NotificationService(NotificationRouter router) {
        this.router = router;
    }

    public void sendRegistrationEmail(String to, String subject, String body) {
        send(new Notification(NotificationChannelType.EMAIL, to, subject, body));
    }

    public void send(Notification notification) {
        router.resolve(notification.channelType()).send(notification);
    }
}

class NotificationException extends RuntimeException {
    NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
