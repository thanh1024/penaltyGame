package server;

import common.Message;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameRoom {

    private ClientHandler shooterHandler;
    private ClientHandler goalkeeperHandler;
    private DatabaseManager dbManager;
    private int matchId;
    private int shooterScore;
    private int goalkeeperScore;
    private int player1Score; // Điểm của người chơi 1 (originalPlayer1)
    private int player2Score; // Điểm của người chơi 2 (originalPlayer2)
    private int currentRound;
    private final int MAX_ROUNDS = 5; // 5 rounds đầu tiên
    private final int WIN_SCORE = 3;
    private String shooterDirection;
    private Boolean shooterWantsRematch = null;
    private Boolean goalkeeperWantsRematch = null;
    // Thời gian chờ cho mỗi lượt (ví dụ: 15 giây)
    private final int TURN_TIMEOUT = 15;

    private boolean isShooter = true;
    private boolean isKeeper = true;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Biến lưu trữ Future của nhiệm vụ chờ
    private ScheduledFuture<?> shooterTimeoutTask;
    private ScheduledFuture<?> goalkeeperTimeoutTask;

    // Biến để kiểm tra xem người chơi đã thực hiện hành động chưa
    private boolean shooterActionReceived = false;
    private boolean goalkeeperActionReceived = false;

    private String goalkeeperDirection;
    
    // Biến cho sudden death mode
    private boolean inSuddenDeath = false;
    private boolean firstPlayerGoalInRound = false; // Kết quả của người sút đầu tiên trong round
    private boolean secondPlayerGoalInRound = false; // Kết quả của người sút thứ hai trong round
    private boolean firstPlayerFinished = false; // Đã xử lý xong lượt sút của người đầu tiên trong round
    private ClientHandler originalPlayer1; // Lưu người chơi 1 ban đầu
    private ClientHandler originalPlayer2; // Lưu người chơi 2 ban đầu

    public GameRoom(ClientHandler player1, ClientHandler player2, DatabaseManager dbManager) throws SQLException {
        this.dbManager = dbManager;
        this.matchId = dbManager.saveMatch(player1.getUser().getId(), player2.getUser().getId(), 0);
        this.shooterScore = 0;
        this.goalkeeperScore = 0;
        this.player1Score = 0;
        this.player2Score = 0;
        this.currentRound = 1;
        
        // Lưu người chơi ban đầu để theo dõi điểm số
        this.originalPlayer1 = player1;
        this.originalPlayer2 = player2;

        // Random chọn người sút và người bắt trong lượt đầu
        if (new Random().nextBoolean()) {
            this.shooterHandler = player1;
            this.goalkeeperHandler = player2;
        } else {
            this.shooterHandler = player2;
            this.goalkeeperHandler = player1;
        }
    }

    public void startMatch() {
        try {
            // update ingame status for both player
            shooterHandler.getUser().setStatus("ingame");
            goalkeeperHandler.getUser().setStatus("ingame");
            
            // Khởi tạo biến cho round đầu tiên
            firstPlayerFinished = false;
            inSuddenDeath = false;

            // to do gui message neu can
            String shooterMessage = "Trận đấu bắt đầu! Bạn là người sút.";
            String goalkeeperMessage = "Trận đấu bắt đầu! Bạn là người bắt.";
            shooterHandler.sendMessage(new Message("match_start", shooterMessage));
            goalkeeperHandler.sendMessage(new Message("match_start", goalkeeperMessage));
            
            // Đợi một chút để client kịp load UI trước khi gửi your_turn
            scheduler.schedule(() -> {
                try {
                    requestNextMove();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestNextMove() {
        try {
            // Không kiểm tra checkEndGame() ở đây vì nó chỉ được gọi sau khi cả 2 lượt đã xong
            // Luôn là shooterHandler sút và goalkeeperHandler bắt
            shooterHandler.sendMessage(new Message("your_turn", TURN_TIMEOUT));
            goalkeeperHandler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));
            
            shooterActionReceived = false;
            shooterDirection = null;
            goalkeeperDirection = null; // Đặt lại biến này cho lượt mới

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Xử lý hướng sút từ người sút
    public synchronized void handleShot(String shooterDirection, ClientHandler shooter)
            throws SQLException, IOException {
        this.shooterDirection = shooterDirection;
        shooterActionReceived = true; // Đánh dấu đã nhận hành động từ người sút
        if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
            shooterTimeoutTask.cancel(true);
        }
        // Yêu cầu người bắt chọn hướng chặn
        // Luôn là goalkeeperHandler bắt và shooterHandler chờ
        goalkeeperHandler.sendMessage(new Message("goalkeeper_turn", TURN_TIMEOUT));
        shooterHandler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));

        // Bắt đầu đếm thời gian chờ cho người bắt
        goalkeeperActionReceived = false;
        // startGoalkeeperTimeout();
    }

    // Xử lý hướng chặn từ người bắt
    public synchronized void handleGoalkeeper(String goalkeeperDirection, ClientHandler goalkeeper)
            throws SQLException, IOException {
        if (this.shooterDirection == null) {
            // Nếu shooterDirection chưa được thiết lập, không thể xử lý
            shooterHandler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            goalkeeperHandler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            return;
        }
        this.goalkeeperDirection = goalkeeperDirection;
        goalkeeperActionReceived = true; // Đánh dấu đã nhận hành động từ người bắt

        // Hủy nhiệm vụ chờ của người bắt nếu còn tồn tại
        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
            goalkeeperTimeoutTask.cancel(true);
        }

        // Xử lý kết quả - so sánh hướng sút và hướng chặn
        boolean goal = !directionsMatch(shooterDirection, goalkeeperDirection);
        
        // Cập nhật điểm số cho người chơi ghi bàn
        if (goal) {
            if (shooterHandler == originalPlayer1) {
                player1Score++;
            } else {
                player2Score++;
            }
        }

        String kick_result = (goal ? "win" : "lose") + "-" + shooterDirection + "-" + goalkeeperDirection;
        shooterHandler.sendMessage(new Message("kick_result", kick_result));
        goalkeeperHandler.sendMessage(new Message("kick_result", kick_result));

        // Lưu chi tiết trận đấu vào database
        dbManager.saveMatchDetails(matchId, currentRound,
                shooterHandler.getUser().getId(),
                goalkeeperHandler.getUser().getId(),
                shooterDirection, goalkeeperDirection, goal ? "win" : "lose");

        if (inSuddenDeath) {
            // Xử lý sudden death mode
            if (!firstPlayerFinished) {
                // Lượt 1 trong round: Người đầu tiên sút
                firstPlayerGoalInRound = goal;
                firstPlayerFinished = true;
                
                // Gửi tỷ số cập nhật
                originalPlayer1.sendMessage(new Message("update_score",
                        new int[] { player1Score, player2Score, currentRound }));
                originalPlayer2.sendMessage(new Message("update_score",
                        new int[] { player2Score, player1Score, currentRound }));
                
                // Chuyển sang lượt 2: Đổi vai trò để người thứ hai sút
                shooterDirection = null;
                goalkeeperDirection = null;
                shooterActionReceived = false;
                goalkeeperActionReceived = false;
                // Đổi vai trò
                ClientHandler temp = shooterHandler;
                shooterHandler = goalkeeperHandler;
                goalkeeperHandler = temp;
                requestNextMove();
            } else {
                // Lượt 2 trong round: Người thứ hai sút
                secondPlayerGoalInRound = goal;
                
                // Gửi tỷ số cập nhật
                originalPlayer1.sendMessage(new Message("update_score",
                        new int[] { player1Score, player2Score, currentRound }));
                originalPlayer2.sendMessage(new Message("update_score",
                        new int[] { player2Score, player1Score, currentRound }));
                
                // So sánh kết quả trong round này
                // Nếu một người ghi và người kia không ghi → kết thúc ngay
                if (firstPlayerGoalInRound != secondPlayerGoalInRound) {
                    // Có sự khác biệt, xác định người thắng
                    determineWinner();
                } else {
                    // Cả hai cùng ghi hoặc cùng trượt, tiếp tục round tiếp theo
                    currentRound++;
                    firstPlayerFinished = false;
                    firstPlayerGoalInRound = false;
                    secondPlayerGoalInRound = false;
                    
                    // Gửi update_score với round mới TRƯỚC KHI gọi requestNextMove
                    originalPlayer1.sendMessage(new Message("update_score",
                            new int[] { player1Score, player2Score, currentRound }));
                    originalPlayer2.sendMessage(new Message("update_score",
                            new int[] { player2Score, player1Score, currentRound }));
                    
                    shooterDirection = null;
                    goalkeeperDirection = null;
                    shooterActionReceived = false;
                    goalkeeperActionReceived = false;
                    // Đổi lại vai trò để người đầu tiên sút trong round mới (luân phiên)
                    ClientHandler temp = shooterHandler;
                    shooterHandler = goalkeeperHandler;
                    goalkeeperHandler = temp;
                    requestNextMove();
                }
            }
        } else {
            // Xử lý bình thường (5 rounds đầu)
            // Gửi tỷ số cập nhật
            originalPlayer1.sendMessage(new Message("update_score",
                    new int[] { player1Score, player2Score, currentRound }));
            originalPlayer2.sendMessage(new Message("update_score",
                    new int[] { player2Score, player1Score, currentRound }));
            
            System.out.println("Round " + currentRound + " - firstPlayerFinished: " + firstPlayerFinished + 
                    ", Player1: " + player1Score + ", Player2: " + player2Score);
            
            if (!firstPlayerFinished) {
                // Lượt 1 trong round: Người đầu tiên sút xong
                System.out.println("Lượt 1 trong round " + currentRound + " đã xong");
                firstPlayerFinished = true;
                
                // Chuyển sang lượt 2: Đổi vai trò để người thứ hai sút
                shooterDirection = null;
                goalkeeperDirection = null;
                shooterActionReceived = false;
                goalkeeperActionReceived = false;
                // Đổi vai trò
                ClientHandler temp = shooterHandler;
                shooterHandler = goalkeeperHandler;
                goalkeeperHandler = temp;
                requestNextMove();
            } else {
                // Lượt 2 trong round: Người thứ hai sút xong
                System.out.println("Lượt 2 trong round " + currentRound + " đã xong, kết thúc round");
                // Kết thúc round, tăng round và kiểm tra điều kiện
                currentRound++;
                firstPlayerFinished = false;
                
                if (checkEndGame()) {
                    determineWinner();
                } else {
                    // Gửi update_score với round mới TRƯỚC KHI gọi requestNextMove
                    originalPlayer1.sendMessage(new Message("update_score",
                            new int[] { player1Score, player2Score, currentRound }));
                    originalPlayer2.sendMessage(new Message("update_score",
                            new int[] { player2Score, player1Score, currentRound }));
                    
                    // Thông báo round tiếp theo
                    shooterDirection = null;
                    goalkeeperDirection = null;
                    shooterActionReceived = false;
                    goalkeeperActionReceived = false;
                    // Đổi lại vai trò để người đầu tiên sút trong round mới (luân phiên)
                    ClientHandler temp = shooterHandler;
                    shooterHandler = goalkeeperHandler;
                    goalkeeperHandler = temp;
                    requestNextMove();
                }
            }
        }
    }

    private void determineWinner() throws SQLException, IOException {
        int winnerId = 0;
        String resultMessage = "";
        String endReason = "normal";
        
        if (player1Score > player2Score) {
            winnerId = originalPlayer1.getUser().getId();
            resultMessage = originalPlayer1.getUser().getUsername() + " thắng trận đấu!";
        } else if (player2Score > player1Score) {
            winnerId = originalPlayer2.getUser().getId();
            resultMessage = originalPlayer2.getUser().getUsername() + " thắng trận đấu!";
        } else {
            resultMessage = "Trận đấu hòa!";
        }

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
        }
        dbManager.updateMatchWinner(matchId, winnerId, endReason);

        // Thông báo kết quả trận đấu cho cả hai người chơi
        originalPlayer1.sendMessage(new Message("match_result", (player1Score > player2Score) ? "win" : "lose"));
        originalPlayer2.sendMessage(new Message("match_result", (player2Score > player1Score) ? "win" : "lose"));

        // Tạo một ScheduledExecutorService để trì hoãn việc gửi tin nhắn
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            // Gửi tin nhắn yêu cầu chơi lại sau 5 giây
            shooterHandler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
            goalkeeperHandler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
            // Đóng scheduler sau khi hoàn tất
            scheduler.shutdown();
        }, 3, TimeUnit.SECONDS);
    }

    // Xử lý yêu cầu chơi lại
    public synchronized void handlePlayAgainResponse(boolean playAgain, ClientHandler responder)
            throws SQLException, IOException {
        if (responder == shooterHandler) {
            shooterWantsRematch = playAgain;
        } else if (responder == goalkeeperHandler) {
            goalkeeperWantsRematch = playAgain;
        }

        // Kiểm tra nếu một trong hai người chơi đã thoát
        if (shooterHandler == null || goalkeeperHandler == null) {
            return;
        }

        // Kiểm tra nếu cả hai người chơi đã phản hồi
        if (shooterWantsRematch != null && goalkeeperWantsRematch != null) {
            if (shooterWantsRematch && goalkeeperWantsRematch) {
                // Cả hai người chơi đồng ý chơi lại
                resetGameState();
                startMatch();
            } else {
                // cap nhat status "ingame" -> "online"
                shooterHandler.getUser().setStatus("online");
                goalkeeperHandler.getUser().setStatus("online");

                dbManager.updateUserStatus(shooterHandler.getUser().getId(), "online");
                dbManager.updateUserStatus(goalkeeperHandler.getUser().getId(), "online");

                shooterHandler.getServer()
                        .broadcast(new Message("status_update", shooterHandler.getUser().getUsername() + " is online"));
                goalkeeperHandler.getServer().broadcast(
                        new Message("status_update", goalkeeperHandler.getUser().getUsername() + " is online"));
                // ------------------------------------------------------------//

                // Gửi thông báo kết thúc trận đấu
                shooterHandler.sendMessage(new Message("match_end", "Trận đấu kết thúc."));
                goalkeeperHandler.sendMessage(new Message("match_end", "Trận đấu kết thúc."));

                // Đặt lại biến
                shooterWantsRematch = null;
                goalkeeperWantsRematch = null;

                // Đưa cả hai người chơi về màn hình chính
                shooterHandler.clearGameRoom();
                goalkeeperHandler.clearGameRoom();
            }
        }
    }

    private void resetGameState() throws SQLException {
        // Reset game variables
        shooterScore = 0;
        goalkeeperScore = 0;
        player1Score = 0;
        player2Score = 0;
        currentRound = 1;
        shooterDirection = null;
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        
        // Reset sudden death variables
        inSuddenDeath = false;
        firstPlayerFinished = false;
        firstPlayerGoalInRound = false;
        secondPlayerGoalInRound = false;

        // Swap shooter and goalkeeper roles for fairness
        ClientHandler temp = shooterHandler;
        shooterHandler = goalkeeperHandler;
        goalkeeperHandler = temp;
        
        // Swap original players too
        temp = originalPlayer1;
        originalPlayer1 = originalPlayer2;
        originalPlayer2 = temp;

        // Create a new match in the database
        matchId = dbManager.saveMatch(shooterHandler.getUser().getId(), goalkeeperHandler.getUser().getId(), 0);
    }

    // Đảm bảo rằng phương thức endMatch() tồn tại và được định nghĩa chính xác
    private void endMatch() throws SQLException, IOException {
        determineWinner();

        // Reset in-game status for both players after match
        if (shooterHandler != null) {
            shooterHandler.getUser().setStatus("online");
            // todo gui message neu can
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.getUser().setStatus("online");
            // todo gui message neu can
        }
    }

    public void handlePlayerDisconnect(ClientHandler disconnectedPlayer) throws SQLException, IOException {
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        int winnerId = 0;
        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        if (disconnectedPlayer == shooterHandler) {
            otherPlayer = goalkeeperHandler;
        } else if (disconnectedPlayer == goalkeeperHandler) {
            otherPlayer = shooterHandler;
        }

        shooterWantsRematch = false;
        goalkeeperWantsRematch = false;
        winnerId = otherPlayer.getUser().getId();

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
        }

        // cap nhat status "ingame" -> "online"
        otherPlayer.getUser().setStatus("online");
        dbManager.updateUserStatus(otherPlayer.getUser().getId(), "online");
        otherPlayer.getServer()
                .broadcast(new Message("status_update", otherPlayer.getUser().getUsername() + " is online"));

        // cap nhat status "ingame" -> "offline"
        disconnectedPlayer.getUser().setStatus("offline");
        dbManager.updateUserStatus(disconnectedPlayer.getUser().getId(), "offline");
        disconnectedPlayer.getServer()
                .broadcast(new Message("status_update", disconnectedPlayer.getUser().getUsername() + " is offline"));
        // -------------------------------------------------------

        // Gửi thông báo kết thúc trận đấu cho cả hai người chơi
        otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
        disconnectedPlayer.sendMessage(new Message("match_end", resultMessageToLoser));

        // Đặt lại trạng thái game room
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        shooterDirection = null;

        // Sử dụng phương thức clearGameRoom() để đặt gameRoom thành null
        if (shooterHandler != null) {
            shooterHandler.clearGameRoom();
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.clearGameRoom();
        }

    }

    public void handlePlayerQuit(ClientHandler quittingPlayer) throws SQLException, IOException {
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";

        int winnerId = 0;
        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        if (quittingPlayer == shooterHandler) {
            winnerId = goalkeeperHandler.getUser().getId();
            otherPlayer = goalkeeperHandler;
            // Cập nhật trạng thái chơi lại
            shooterWantsRematch = false;
        } else if (quittingPlayer == goalkeeperHandler) {
            winnerId = shooterHandler.getUser().getId();
            otherPlayer = shooterHandler;
            // Cập nhật trạng thái chơi lại
            goalkeeperWantsRematch = false;
        }

        winnerId = otherPlayer.getUser().getId();

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
        }

        // cap nhat status "ingame" -> "online"
        shooterHandler.getUser().setStatus("online");
        goalkeeperHandler.getUser().setStatus("online");

        dbManager.updateUserStatus(shooterHandler.getUser().getId(), "online");
        dbManager.updateUserStatus(goalkeeperHandler.getUser().getId(), "online");

        shooterHandler.getServer()
                .broadcast(new Message("status_update", shooterHandler.getUser().getUsername() + " is online"));
        goalkeeperHandler.getServer()
                .broadcast(new Message("status_update", goalkeeperHandler.getUser().getUsername() + " is online"));
        // ------------------------------------------------------------

        // Gửi thông báo kết thúc trận đấu cho cả hai người chơi
        quittingPlayer.sendMessage(new Message("match_end", resultMessageToLoser));
        if (otherPlayer != null) {
            otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
        }

        // Đặt lại trạng thái game room
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        shooterDirection = null;

        // Sử dụng phương thức clearGameRoom() để đặt gameRoom thành null
        if (shooterHandler != null) {
            shooterHandler.clearGameRoom();
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.clearGameRoom();
        }

        // Không cần gửi thông báo "return_to_main"
    }

    public void startShooterTimeout() {
        try {
            if (checkEndGame()) {
                endMatch();
                return;
            }
            if (!shooterActionReceived) {
                // Người sút không thực hiện hành động trong thời gian quy định
                shooterDirection = "Middle";
                shooterActionReceived = true;
                shooterHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn 'Middle' cho bạn."));
                goalkeeperHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn 'Middle' cho đối thủ."));
                // Yêu cầu người bắt chọn hướng chặn
                handleShot(shooterDirection, shooterHandler);

                // Bắt đầu đếm thời gian chờ cho người bắt
                goalkeeperActionReceived = false;
                // startGoalkeeperTimeout();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Phương thức kiểm tra xem hai hướng có khớp nhau không
    // Chỉ khớp khi hướng giống hệt nhau (chính xác)
    private boolean directionsMatch(String shooterDir, String goalkeeperDir) {
        if (shooterDir == null || goalkeeperDir == null) {
            return false;
        }
        
        // Chuẩn hóa chuỗi (loại bỏ khoảng trắng thừa và chuyển thành chữ thường)
        String shooter = shooterDir.trim().toLowerCase();
        String goalkeeper = goalkeeperDir.trim().toLowerCase();
        
        // Chỉ khớp khi giống hệt nhau
        return shooter.equals(goalkeeper);
    }

    private boolean checkEndGame() {
        // Nếu đang trong sudden death, không kiểm tra ở đây (đã xử lý trong handleGoalkeeper)
        if (inSuddenDeath) {
            return false;
        }
        
        // Nếu đã qua 5 rounds và tỷ số hòa, chuyển sang sudden death
        if (currentRound > MAX_ROUNDS && player1Score == player2Score) {
            inSuddenDeath = true;
            firstPlayerFinished = false;
            firstPlayerGoalInRound = false;
            secondPlayerGoalInRound = false;
            // Không kết thúc, tiếp tục với sudden death
            return false;
        }
        
        // Kiểm tra các điều kiện thắng khác
        int scoreDifference = Math.abs(player1Score - player2Score);
        int roundsPlayed = currentRound - 1; // currentRound đã được tăng lên
        int turnsLeftPlayer1 = (player1Score >= WIN_SCORE) ? 0 : WIN_SCORE - (roundsPlayed / 2);
        int turnsLeftPlayer2 = (player2Score >= WIN_SCORE) ? 0 : WIN_SCORE - (roundsPlayed / 2);
        
        System.out.println("Round: " + currentRound + ", Player1: " + player1Score + ", Player2: " + player2Score);
        
        return ((turnsLeftPlayer1 < scoreDifference) && player1Score < player2Score) ||
                ((turnsLeftPlayer2 < scoreDifference) && player2Score < player1Score)
                || ((currentRound > MAX_ROUNDS || player1Score >= WIN_SCORE || player2Score >= WIN_SCORE)
                        && player1Score != player2Score);
    }

    public void startGoalkeeperTimeout() {
        try {
            if (!goalkeeperActionReceived) {
                // Người bắt không thực hiện hành động trong thời gian quy định
                goalkeeperDirection = "Middle";
                goalkeeperActionReceived = true;

                goalkeeperHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn 'Middle' cho bạn."));
                shooterHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn 'Middle' cho đối thủ."));

                // Tiến hành xử lý kết quả
                handleGoalkeeper(goalkeeperDirection, goalkeeperHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
