package com.example.financial_management.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@example.com}")
    private String fromEmail;

    public void sendResetPasswordEmail(String toEmail, String userName, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Yêu cầu đặt lại mật khẩu - Financial Management");

            String htmlContent = "<div style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333;\">"
                    + "<h2>Xin chào " + (userName != null ? userName : "bạn") + ",</h2>"
                    + "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>"
                    + "<p>Vui lòng bấm vào liên kết bên dưới để tạo mật khẩu mới (Liên kết có hiệu lực trong 15 phút):</p>"
                    + "<p style=\"margin: 24px 0;\">"
                    + "<a href=\"" + resetLink
                    + "\" style=\"background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px; font-weight: bold;\">Đặt lại mật khẩu</a>"
                    + "</p>"
                    + "<p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>"
                    + "<br/>"
                    + "<p>Trân trọng,<br/><strong>Financial Management Team</strong></p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email reset password đã gửi thành công tới: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email đặt lại mật khẩu tới {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Không thể gửi email đặt lại mật khẩu, vui lòng thử lại sau.");
        }
    }
}