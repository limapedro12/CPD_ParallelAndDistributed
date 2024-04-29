package server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

import utils.Pair;

public class Player {
    // represents a Player from the perspective of the server

    private static Map<Pair<String, String>, Player> loggedPlayers = new HashMap<Pair<String, String>, Player>();
    private static Map<String, Player> playersByToken = new HashMap<String, Player>();

    public static ReentrantLock lockLoggedPlayers = new ReentrantLock();
    public static ReentrantLock lockPlayersByToken = new ReentrantLock();

    private static ReentrantLock databaseLock = new ReentrantLock();

    public ReentrantLock lockPlayer = new ReentrantLock();

    private String currentToken;
    private Socket currentSocket;

    private String username;
    private String password;

    private int points = 0;

    public Player(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static Player login(String username, String password, Socket socket) {
        lockLoggedPlayers.lock();
        Player player;
        if (loggedPlayers.containsKey(new Pair<String, String>(username, password))) {
            player = loggedPlayers.get(new Pair<String, String>(username, password));
        } else if (existsInDatabase(username, password)) {
            player = new Player(username, password);

            player.lockPlayer.lock();
            player.generateToken();
            player.lockPlayer.unlock();

            loggedPlayers.put(new Pair<String, String>(username, password), player);

            lockPlayersByToken.lock();
            playersByToken.put(player.getToken(), player);
            lockPlayersByToken.unlock();

        } else {
            lockLoggedPlayers.unlock();
            return null;
        }

        lockLoggedPlayers.unlock();

        player.lockPlayer.lock();
        player.currentSocket = socket;
        player.lockPlayer.unlock();

        return player;
    }

    public static Player getPlayerByToken(String token) {
        lockPlayersByToken.lock();
        Player p = playersByToken.get(token);
        lockPlayersByToken.unlock();

        return p;
    }

    public static void logout(Player player) {
        lockLoggedPlayers.lock();
        loggedPlayers.remove(new Pair<String, String>(player.username, player.password));
        lockLoggedPlayers.unlock();
    }

    private void generateToken() {
        this.lockPlayer.lock();
        this.currentToken = this.username + Integer.toString((int) (Math.random() * 1000000));
        this.lockPlayer.unlock();
    }

    public String getToken() {
        this.lockPlayer.lock();
        String token = this.currentToken;
        this.lockPlayer.unlock();
        return token;
    }

    private static boolean existsInDatabase(String username, String password) {
        databaseLock.lock();
        try {
            String working_dir = System.getProperty("user.dir");
            File file = new File(working_dir + "/server/storage/players.csv");
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] data = line.split(",");
                String storedUsername = data[0];
                String storedPassword = data[1];
                if (storedUsername.equals(username) && storedPassword.equals(password)) {
                    scanner.close();
                    databaseLock.unlock();
                    return true;
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error: players.csv file not found.");
        }
        databaseLock.unlock();
        return false;
    }

    public void incrementPoints(int inc) {
        this.lockPlayer.lock();
        this.points += inc;
        this.lockPlayer.unlock();
    }

    public void decrementPoints(int dec) {
        this.lockPlayer.lock();
        this.points = Math.max(0, this.points-dec);
        this.lockPlayer.unlock();
    }

    public void send(String message) {
        this.lockPlayer.lock();
        try {
            OutputStream output = this.currentSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.println(message);
        } catch (IOException ex) {
            System.out.println("Cannot send message to player " + this.username);
        }
        this.lockPlayer.unlock();
    }
}
