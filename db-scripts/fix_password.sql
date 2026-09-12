-- Cập nhật password hash đúng cho các tài khoản test
-- Mật khẩu gốc: "123456"  
-- BCrypt hash được generate bởi PasswordHashGenerator.java

UPDATE users 
SET password_hash = '$2a$10$bjghLtvGiUGlA.x9PNzutOMFvUq6geU35QYsjKqxjcsyaqCDvl2Sm'
WHERE phone_number IN ('0900000001','0900000002','0900000003','0900000004','0900000005');

-- Xác nhận
SELECT phone_number, LEFT(password_hash, 7) as bcrypt_prefix, role 
FROM users 
ORDER BY id;
