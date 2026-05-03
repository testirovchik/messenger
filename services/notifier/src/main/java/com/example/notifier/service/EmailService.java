package com.example.notifier.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendMissedMessageEmail(String toEmail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("your-email@gmail.com");
            message.setTo(toEmail);
            message.setSubject("You have a new message!");
            message.setText("Hello,\n\nYou just received a new message while you were offline. Log back in to read it!\n\nCheers,\nErik's Messenger");

            mailSender.send(message);
            System.out.println("📧 Missed message email sent to: " + toEmail);

        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }
}
