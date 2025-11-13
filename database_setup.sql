-- Script tạo database và các bảng cho dự án Penalty Shootout
-- Chạy script này trong MySQL để tạo database và các bảng cần thiết

-- Tạo database
CREATE DATABASE IF NOT EXISTS penalty_shootout;
USE penalty_shootout;

-- Tạo bảng users (người dùng)
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    points INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'offline',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tạo bảng matches (trận đấu)
CREATE TABLE IF NOT EXISTS matches (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player1_id INT NOT NULL,
    player2_id INT NOT NULL,
    winner_id INT NULL,
    end_reason VARCHAR(50) NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (player2_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Tạo bảng match_details (chi tiết trận đấu)
CREATE TABLE IF NOT EXISTS match_details (
    id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT NOT NULL,
    round INT NOT NULL,
    shooter_id INT NOT NULL,
    goalkeeper_id INT NOT NULL,
    shooter_direction VARCHAR(20),
    goalkeeper_direction VARCHAR(20),
    result VARCHAR(20),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    FOREIGN KEY (shooter_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (goalkeeper_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Tạo một số user mẫu để test (tùy chọn)
-- Mật khẩu: password123 (bạn có thể thay đổi)
INSERT INTO users (username, password, points, status) VALUES
('player1', 'password123', 0, 'offline'),
('player2', 'password123', 0, 'offline'),
('admin', 'admin123', 0, 'offline')
ON DUPLICATE KEY UPDATE username=username;

-- Hiển thị thông báo thành công
SELECT 'Database và các bảng đã được tạo thành công!' AS Message;


