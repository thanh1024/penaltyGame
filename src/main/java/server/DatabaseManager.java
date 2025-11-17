package server;

import common.Match;
import common.User;
import common.MatchDetails;
import common.Pair;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String URL = "jdbc:mysql://localhost:3306/penalty_shootout";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    private Connection conn;

    static {
        // Load MySQL driver (hỗ trợ version 8.0+ và 9.0+)
        try {
            // Thử load driver mới nhất (MySQL Connector/J 8.0+)
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            try {
                // Fallback: thử driver cũ (MySQL Connector/J 5.x)
                Class.forName("com.mysql.jdbc.Driver");
                System.out.println("MySQL Driver (legacy) loaded successfully.");
            } catch (ClassNotFoundException ex) {
                System.err.println("ERROR: MySQL Driver not found!");
                System.err.println("Make sure mysql-connector-j-*.jar is in classpath.");
                System.err.println("Download from: https://dev.mysql.com/downloads/connector/j/");
                ex.printStackTrace();
            }
        }
    }

    public DatabaseManager() throws SQLException {
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connected to MySQL database: penalty_shootout");
        } catch (SQLException e) {
            System.err.println("Failed to connect to MySQL database!");
            System.err.println("URL: " + URL);
            System.err.println("User: " + USER);
            System.err.println("Check: 1) MySQL is running, 2) Database exists, 3) Username/Password correct");
            throw e;
        }
    }

    // Phương thức đăng ký
    public boolean registerUser(String username, String password) throws SQLException {
        System.out.println("[DEBUG DB] registerUser called - Username: " + username);
        
        // Kiểm tra xem username đã tồn tại chưa
        String checkQuery = "SELECT * FROM users WHERE username = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
        checkStmt.setString(1, username);
        System.out.println("[DEBUG DB] Executing query to check if username exists");
        ResultSet rs = checkStmt.executeQuery();
        
        if (rs.next()) {
            // Username đã tồn tại
            System.out.println("[DEBUG DB] Username already exists: " + username);
            return false;
        }
        
        System.out.println("[DEBUG DB] Username is available, inserting new user");
        // Thêm user mới vào database
        String insertQuery = "INSERT INTO users (username, password, points, status) VALUES (?, ?, 0, 'offline')";
        PreparedStatement insertStmt = conn.prepareStatement(insertQuery);
        insertStmt.setString(1, username);
        insertStmt.setString(2, password);
        System.out.println("[DEBUG DB] Executing INSERT query");
        int result = insertStmt.executeUpdate();
        System.out.println("[DEBUG DB] INSERT result: " + result + " rows affected");
        
        return result > 0;
    }
    
    // Phương thức đăng nhập
    public Pair<User, Boolean> authenticate(String username, String password) throws SQLException {
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, username);
        stmt.setString(2, password);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            User authenticatedUser = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getInt("points"),
                    rs.getString("status"));
            Boolean isOffline = rs.getString("status").equals("offline");
            return new Pair<>(authenticatedUser, isOffline);

        }
        return new Pair<>(null, null);
    }

    // Cập nhật trạng thái người dùng
    public void updateUserStatus(int userId, String status) throws SQLException {
        String query = "UPDATE users SET status = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, status);
        stmt.setInt(2, userId);
        stmt.executeUpdate();
    }
    
    // Reset tất cả users về offline (dùng khi server khởi động)
    public void resetAllUsersStatus() throws SQLException {
        String query = "UPDATE users SET status = 'offline' WHERE status != 'offline'";
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(query);
    }

    // Lấy danh sách người chơi
    public List<User> getUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM users";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            users.add(new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getInt("points"),
                    rs.getString("status")));
        }
        return users;
    }

    // Lưu lịch sử đấu
    public int saveMatch(int player1Id, int player2Id, int winnerId) throws SQLException {
        String query = "INSERT INTO matches (player1_id, player2_id, winner_id) VALUES (?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, player1Id);
        stmt.setInt(2, player2Id);
        if (winnerId > 0) {
            stmt.setInt(3, winnerId);
        } else {
            stmt.setNull(3, Types.INTEGER);
        }
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        if (rs.next()) {
            return rs.getInt(1);
        }
        return -1;
    }

    // Cập nhật người chiến thắng vào lịch sử đấu
    public void updateMatchWinner(int matchId, int winnerId, String endReason) throws SQLException {
        String query = "UPDATE matches SET winner_id = ?, end_reason = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, winnerId);
        stmt.setString(2, endReason);
        stmt.setInt(3, matchId);
        stmt.executeUpdate();
    }

    // Cập nhật điểm số
    public void updateUserPoints(int userId, int points) throws SQLException {
        String query = "UPDATE users SET points = points + ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, points);
        stmt.setInt(2, userId);
        stmt.executeUpdate();
    }

    // Phương thức lưu chi tiết trận đấu
    public void saveMatchDetails(int matchId, int round, int shooterId, int goalkeeperId, String shooterDirection,
            String goalkeeperDirection, String result) throws SQLException {
        String query = "INSERT INTO match_details (match_id, round, shooter_id, goalkeeper_id, shooter_direction, goalkeeper_direction, result) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, matchId);
        stmt.setInt(2, round);
        stmt.setInt(3, shooterId);
        stmt.setInt(4, goalkeeperId);
        stmt.setString(5, shooterDirection);
        stmt.setString(6, goalkeeperDirection);
        stmt.setString(7, result);
        stmt.executeUpdate();
    }

    // Lấy lịch sử đấu theo match ID
    public List<MatchDetails> getMatchDetails(int matchId) throws SQLException {
        List<MatchDetails> detailsList = new ArrayList<>();
        String query = "SELECT *, timestamp AS time FROM match_details WHERE match_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, matchId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            detailsList.add(new MatchDetails(
                    rs.getInt("id"),
                    rs.getInt("match_id"),
                    rs.getInt("round"),
                    rs.getInt("shooter_id"),
                    rs.getInt("goalkeeper_id"),
                    rs.getString("shooter_direction"),
                    rs.getString("goalkeeper_direction"),
                    rs.getString("result"),
                    rs.getTimestamp("time")));
        }
        if (detailsList.isEmpty()) {
            // Kiểm tra lý do kết thúc trận đấu
            String matchQuery = "SELECT winner_id, player1_id, player2_id, end_reason FROM matches WHERE id = ?";
            PreparedStatement matchStmt = conn.prepareStatement(matchQuery);
            matchStmt.setInt(1, matchId);
            ResultSet matchRs = matchStmt.executeQuery();
            if (matchRs.next()) {
                String endReason = matchRs.getString("end_reason");
                int winnerId = matchRs.getInt("winner_id");
                int player1Id = matchRs.getInt("player1_id");
                int player2Id = matchRs.getInt("player2_id");
                if ("player_quit".equals(endReason)) {
                    // Tạo MatchDetails để hiển thị lý do
                    int quitterId = (winnerId == player1Id) ? player2Id : player1Id;
                    detailsList.add(new MatchDetails(
                            0, // id
                            matchId,
                            0, // round
                            quitterId,
                            0, // goalkeeperId
                            null,
                            null,
                            "Player quit",
                            null));
                }
            }
        }
        return detailsList;
    }

    // Các phương thức khác như lấy lịch sử đấu, bảng xếp hạng, v.v.
    public List<User> getLeaderboard() throws SQLException {
        List<User> users = new ArrayList<>();
        // Query để tính số bàn thắng ghi được và số bàn bắt được
        String query = "SELECT u.id, u.username, u.points, u.status, " +
                "COALESCE(goals_scored.count, 0) AS goals_scored, " +
                "COALESCE(goals_saved.count, 0) AS goals_saved " +
                "FROM users u " +
                "LEFT JOIN ( " +
                "    SELECT shooter_id AS user_id, COUNT(*) AS count " +
                "    FROM match_details " +
                "    WHERE result = 'win' " +
                "    GROUP BY shooter_id " +
                ") AS goals_scored ON u.id = goals_scored.user_id " +
                "LEFT JOIN ( " +
                "    SELECT goalkeeper_id AS user_id, COUNT(*) AS count " +
                "    FROM match_details " +
                "    WHERE result = 'lose' " +
                "    GROUP BY goalkeeper_id " +
                ") AS goals_saved ON u.id = goals_saved.user_id " +
                "ORDER BY u.points DESC, goals_scored DESC, goals_saved DESC";
        
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            User user = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getInt("points"),
                    rs.getString("status"));
            // Lưu stats vào User object bằng cách thêm vào một map hoặc tạo UserStats
            // Tạm thời chỉ trả về User, sẽ cần tạo UserStats class
            users.add(user);
        }
        return users;
    }
    
    // Phương thức mới để lấy leaderboard với stats
    public List<common.UserStats> getLeaderboardWithStats() throws SQLException {
        List<common.UserStats> statsList = new ArrayList<>();
        String query = "SELECT u.id, u.username, u.points, u.status, " +
                "COALESCE(goals_scored.count, 0) AS goals_scored, " +
                "COALESCE(goals_saved.count, 0) AS goals_saved " +
                "FROM users u " +
                "LEFT JOIN ( " +
                "    SELECT shooter_id AS user_id, COUNT(*) AS count " +
                "    FROM match_details " +
                "    WHERE result = 'win' " +
                "    GROUP BY shooter_id " +
                ") AS goals_scored ON u.id = goals_scored.user_id " +
                "LEFT JOIN ( " +
                "    SELECT goalkeeper_id AS user_id, COUNT(*) AS count " +
                "    FROM match_details " +
                "    WHERE result = 'lose' " +
                "    GROUP BY goalkeeper_id " +
                ") AS goals_saved ON u.id = goals_saved.user_id " +
                "ORDER BY u.points DESC, goals_scored DESC, goals_saved DESC";
        
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            User user = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getInt("points"),
                    rs.getString("status"));
            int goalsScored = rs.getInt("goals_scored");
            int goalsSaved = rs.getInt("goals_saved");
            statsList.add(new common.UserStats(user, goalsScored, goalsSaved));
        }
        return statsList;
    }

    // Lấy lịch sử đấu chi tiết theo UserID
    public List<MatchDetails> getUserMatchHistory(int userId) throws SQLException {
        List<MatchDetails> history = new ArrayList<>();
        String query = "SELECT md.*, md.timestamp AS time FROM match_details md "
                + "JOIN matches m ON md.match_id = m.id "
                + "WHERE m.player1_id = ? OR m.player2_id = ? ORDER BY md.match_id DESC, md.round ASC";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, userId);
        stmt.setInt(2, userId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            history.add(new MatchDetails(
                    rs.getInt("id"),
                    rs.getInt("match_id"),
                    rs.getInt("round"),
                    rs.getInt("shooter_id"),
                    rs.getInt("goalkeeper_id"),
                    rs.getString("shooter_direction"),
                    rs.getString("goalkeeper_direction"),
                    rs.getString("result"),
                    rs.getTimestamp("time") // Lấy cột timestamp
            ));
        }
        return history;
    }

    // Lấy lịch sử đấu theo UserID
    public List<Match> getUserMatches(int userId) throws SQLException {
        List<Match> matches = new ArrayList<>();
        String query = "SELECT m.*, m.timestamp AS time, u1.username AS player1_name, u2.username AS player2_name FROM matches m "
                + "JOIN users u1 ON m.player1_id = u1.id "
                + "JOIN users u2 ON m.player2_id = u2.id "
                + "WHERE m.player1_id = ? OR m.player2_id = ? ORDER BY m.id DESC";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, userId);
        stmt.setInt(2, userId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            matches.add(new Match(
                    rs.getInt("id"),
                    rs.getInt("player1_id"),
                    rs.getInt("player2_id"),
                    rs.getObject("winner_id") != null ? rs.getInt("winner_id") : null,
                    rs.getString("player1_name"),
                    rs.getString("player2_name"),
                    rs.getTimestamp("time"),
                    rs.getString("end_reason") // Thêm end_reason
            ));
        }
        return matches;
    }

}
