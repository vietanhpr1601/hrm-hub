package hrm.service;

import hrm.model.User;
import hrm.security.PermissionPolicy;
import hrm.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Password confirmation is checked against the current database account on every Excel operation. */
@Service
public class StaffExcelConfirmationService {
    private final SecurityUtils securityUtils;
    private final PasswordEncoder passwordEncoder;

    public StaffExcelConfirmationService(SecurityUtils securityUtils, PasswordEncoder passwordEncoder) {
        this.securityUtils = securityUtils;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public void confirmEncodedHeader(String encodedPassword) {
        String password = encodedPassword;
        if (encodedPassword != null) {
            try {
                password = URLDecoder.decode(encodedPassword, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Dữ liệu xác nhận mật khẩu không hợp lệ. Vui lòng nhập lại mật khẩu.");
            }
        }
        confirmPassword(password);
    }

    @Transactional(readOnly = true)
    public void confirmPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập mật khẩu của tài khoản đang đăng nhập để xác nhận.");
        }
        User current = securityUtils.getCurrentUser();
        if (!PermissionPolicy.active(current) || !Boolean.TRUE.equals(current.getActive())) {
            throw new IllegalArgumentException("Không thể xác nhận tài khoản đang đăng nhập. Vui lòng đăng nhập lại.");
        }
        boolean matches;
        try {
            matches = passwordEncoder.matches(password, current.getPassword());
        } catch (IllegalArgumentException error) {
            matches = false;
        }
        if (!matches) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không đúng. Vui lòng nhập mật khẩu của tài khoản đang đăng nhập.");
        }
    }
}
