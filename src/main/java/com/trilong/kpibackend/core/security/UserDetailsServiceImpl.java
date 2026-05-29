package com.trilong.kpibackend.core.security;

import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl — tải thông tin user từ DB theo phone number.
 *
 * Spring Security dùng service này để:
 * 1. Validate credentials khi sử dụng DaoAuthenticationProvider
 * 2. Là dependency bắt buộc cho cấu hình AuthenticationManager
 *
 * Trong luồng JWT của chúng ta, service này được dùng bởi AuthService.login()
 * để load user, còn JwtAuthFilter tạo UserPrincipal trực tiếp từ token claims
 * mà không cần gọi service này ở mỗi request.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Load user bằng số điện thoại (đây là "username" trong hệ thống của chúng ta).
     * @param phoneNumber Số điện thoại của nhân viên
     * @throws UsernameNotFoundException Nếu không tìm thấy tài khoản
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Không tìm thấy tài khoản với số điện thoại: " + phoneNumber));
        return UserPrincipal.from(user);
    }
}
