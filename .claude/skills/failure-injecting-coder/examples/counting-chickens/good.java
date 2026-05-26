// 正道：いま必要なのは「メール送信」1種類だけ。
// Spring Boot の JavaMailSender に直接依存して実装する。
// SMS や LINE の話はロードマップ上のコミットされた予定では無いので、抽象化しない。
// もしいつか追加されたら、そのときに変化点を切り出す（Rule of Three）。

package com.example.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendRegistrationEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send registration email: to={}", to, e);
            throw new NotificationException("registration email send failed", e);
        }
    }
}

class NotificationException extends RuntimeException {
    NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
