package common;

import java.io.Serializable;

public class UserStats implements Serializable {
    private User user;
    private int goalsScored;
    private int goalsSaved;

    public UserStats(User user, int goalsScored, int goalsSaved) {
        this.user = user;
        this.goalsScored = goalsScored;
        this.goalsSaved = goalsSaved;
    }

    public User getUser() {
        return user;
    }

    public int getGoalsScored() {
        return goalsScored;
    }

    public int getGoalsSaved() {
        return goalsSaved;
    }

    public int getPoints() {
        return user.getPoints();
    }

    public String getUsername() {
        return user.getUsername();
    }

    public int getId() {
        return user.getId();
    }

    public String getStatus() {
        return user.getStatus();
    }
}

