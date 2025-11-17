package client.GUI;

import client.Client;
import common.Message;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    private Client client;

    public void setClient(Client client) {
        this.client = client;
    }

    @FXML
    private void handleRegister() {
        try {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            
            System.out.println("[DEBUG] Register button clicked");
            System.out.println("[DEBUG] Username: " + username);
            System.out.println("[DEBUG] Password: " + password);
            
            if (client == null) {
                System.err.println("[ERROR] Client is null!");
                showError("Lỗi: Chưa kết nối tới server");
                return;
            }
            
            System.out.println("[DEBUG] Client is connected");
            
            String[] credentials = {username, password};
            Message registerMessage = new Message("register", credentials);
            
            System.out.println("[DEBUG] Sending register message to server...");
            client.sendMessage(registerMessage);
            System.out.println("[DEBUG] Register message sent successfully");
            
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to send register message: " + e.getMessage());
            e.printStackTrace();
            showError("Lỗi khi gửi yêu cầu đăng ký: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            showError("Lỗi không xác định: " + e.getMessage());
        }
    }

    @FXML
    private void handleBackToLogin() throws IOException {
        // Gọi client để chuyển về màn hình đăng nhập
        client.showLoginUI();
    }

    public void showError(String error) {
        Platform.runLater(() -> {
            System.out.println("[DEBUG] Showing error alert: " + error);
            errorLabel.setText(error);
            
            // Hiển thị popup alert cho lỗi
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi đăng ký");
            alert.setHeaderText(null);
            alert.setContentText(error);
            alert.showAndWait();
        });
    }
    
    public void showSuccess(String message) {
        Platform.runLater(() -> {
            System.out.println("[DEBUG] Showing success alert: " + message);
            errorLabel.setText(""); // Xóa error label
            
            // Hiển thị popup alert cho thành công
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Đăng ký thành công");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
            
            // Chuyển về màn hình đăng nhập
            try {
                handleBackToLogin();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
