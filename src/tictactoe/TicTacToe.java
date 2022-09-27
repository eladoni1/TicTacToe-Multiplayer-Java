package tictactoe;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class TicTacToe implements Runnable {
    @SuppressWarnings("unused")

    private String ip = "localhost";
    private int port = 22222;
    private Scanner scanner = new Scanner(System.in);
    private JFrame frame;
    private final int WIDTH = 506;
    private final int HEIGHT = 527;
    private Thread thread;

    private Painter painter;
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;
    private ServerSocket serverSocket;

    private BufferedImage board;
    private BufferedImage redX;
    private BufferedImage redCircle;
    private BufferedImage blueX;
    private BufferedImage blueCircle;

    private String[] spaces = new String[9];
    private boolean yourTurn = false;
    private boolean circle = true;
    private boolean accepted = false;
    private boolean unableToCommunicateToOpponent = false;
    private boolean won = false;
    private boolean enemyWon = false;

    private boolean tie = false;

    private int lengthOfSpace = 160;
    private int errors = 0;
    private int firstSpot = -1;
    private int secondSpot = -1;

    private Font font = new Font("Verdana", Font.BOLD, 32);
    private Font smallerFont = new Font("Verdana", Font.BOLD, 20);
    private Font largerFont = new Font("Verdana", Font.BOLD, 50);

    private String waitingString = "Waiting for another player";
    private String unableToCommunicateWithOpponentString = "unable to communicate with opponent.";
    private String wonString = "You Won!";
    private String enemyWonString = "Opponent Won!";
    private String tieString = "Game ended in a tie!";
    private int[][] wins = new int[][] {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Horizontal wins
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Vertical wins
            {0, 4, 8}, {2, 4, 6} // Diagonal wins
    };

    // Addition
    private JButton restartJButton = null;
    // Addition

    public TicTacToe() {
        System.out.println("Please input the IP: ");
        ip = scanner.nextLine();
        System.out.println("Please input the port: ");
        port = scanner.nextInt();
        while (port < 1 || port > 65535) {
            System.out.println("The port you entered was invalid, please input another port: ");
            port = scanner.nextInt();
        }

        loadImages();
        painter = new Painter();
        painter.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        if (!connect()) initializeServer();
        frame = new JFrame();
        frame.setTitle("Tic-Tac-Toe");
        frame.setContentPane(painter);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);

        // Addition
        restartJButton = new JButton("Restart!");
        restartJButton.setEnabled(false);
        int buttonWidth = 80;
        int buttonHeight = 30;
        restartJButton.setBounds(WIDTH / 2 - buttonWidth / 2, 5, buttonWidth, buttonHeight);
        painter.add(restartJButton);
        restartJButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent click) {
                restart();
                try {
                    dos.writeInt(-1);
                    dos.flush();
                } catch (IOException e1) {
                    errors++;
                    e1.printStackTrace();
                }
            }
        });
        // Addition

        thread = new Thread(this, "TicTacToe");
        thread.start();
    }

    public void run() {
        while(true) {
            if (neitherWon()) {
                tick();
            } else if (serverSocket == null) {
                try {
                    dis.readInt();
                    restart();
                } catch (IOException e) {
                    errors++;
                    e.printStackTrace();
                }
            }
            painter.repaint();

            if (!circle && !accepted) {
                listenForServerRequest();
            }
        }
    }

    public boolean neitherWon() {
        return (!won && !tie && !enemyWon);
    }
    private void render(Graphics g) {
        g.drawImage(board, 0, 0, null);
        if (unableToCommunicateToOpponent) {
            g.setColor(Color.red);
            g.setFont(smallerFont);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); // smooth text (not pixelated)
            int stringWidth = g2.getFontMetrics().stringWidth(unableToCommunicateWithOpponentString);
            g.drawString(unableToCommunicateWithOpponentString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
            return;
        }

        if (accepted) {
            for (int i = 0; i < spaces.length; i++) {
                if (spaces[i] != null) {
                    if (spaces[i].equals("X")) {
                        if (circle) {
                            g.drawImage(redX, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
                        } else {
                            g.drawImage(blueX, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
                        }
                    } else if (spaces[i].equals("O")) {
                        if (circle) {
                            g.drawImage(blueCircle, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
                        } else {
                            g.drawImage(redCircle, (i % 3) * lengthOfSpace + 10 * (i % 3), (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
                        }
                    }
                }
            }
            if (won || enemyWon) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setStroke(new BasicStroke((10)));
                g.setColor(Color.BLACK);
                g.drawLine(firstSpot % 3 * lengthOfSpace + 10 * firstSpot % 3 + lengthOfSpace / 2, (int) (firstSpot / 3) * lengthOfSpace + 10 * (int) (firstSpot / 3) + lengthOfSpace / 2, secondSpot % 3 * lengthOfSpace + 10 * secondSpot % 3 + lengthOfSpace / 2, (int) (secondSpot / 3) * lengthOfSpace + 10 * (int) (secondSpot / 3) + lengthOfSpace / 2);

                g.setColor(Color.RED);
                g.setFont(largerFont);
                if (won) {
                    int stringWidth = g2.getFontMetrics().stringWidth(wonString);
                    g.drawString(wonString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
                } else if (enemyWon) {
                    int stringWidth = g2.getFontMetrics().stringWidth(enemyWonString);
                    g.drawString(enemyWonString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
                }
            }
            if (tie) {
                Graphics2D g2 = (Graphics2D) g;
                g.setColor(Color.BLACK);
                g.setFont(largerFont);
                int stringWidth = g2.getFontMetrics().stringWidth(tieString);
                g.drawString(tieString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
            }
        } else {
            g.setColor(Color.RED);
            g.setFont(font);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int stringWidth = g2.getFontMetrics().stringWidth(waitingString);
            g.drawString(waitingString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
        }

    }

    // Addition
    private void restart() {
        restartJButton.setEnabled(false);
        errors = 0;
        firstSpot = -1;
        secondSpot = -1;
        if (serverSocket != null) {
            yourTurn = true;
            circle = false;
        } else {
            yourTurn = false;
            circle = true;
        }
        spaces = new String[9];
        tie = false;
        won = false;
        enemyWon = false;
//        painter.repaint();
    }
    // Addition
    private void tick() {
        System.out.println("we won: " + Boolean.toString(won));
        System.out.println("opponent won: " + Boolean.toString(enemyWon));
        if (errors >= 10) {
            unableToCommunicateToOpponent = true;
        }
        checkForWin();
        checkForEnemyWin();
        checkForTie();
        if ((won || tie || enemyWon) && !unableToCommunicateToOpponent) {
            try {
                if (serverSocket == null) {
                    int whatToDo = dis.readInt();
                    restart();
                    return;
                } else {
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                errors++;
            }
        }
        if (!yourTurn && !unableToCommunicateToOpponent) {
            try {
                int space = dis.readInt();
                if (circle) spaces[space] = "X";
                else spaces[space] = "O";
                checkForEnemyWin();
                checkForTie();
                yourTurn = true;
            }catch (IOException e) {
                e.printStackTrace();
                errors++;
            }
        }
    }
    private void checkForWin() {
        for (int[] win : wins) {
            if (circle) {
                if (spaces[win[0]] != null && spaces[win[0]].equals("O") && spaces[win[1]] != null && spaces[win[1]].equals("O") && spaces[win[2]] != null && spaces[win[2]].equals("O")) {
                    firstSpot = win[0];
                    secondSpot = win[2];
                    won = true;
                    // Addition
                    if (serverSocket != null) {
                        restartJButton.setEnabled(true);
                    }
                    yourTurn = false;
                    // Addition
                }
            } else {
                if (spaces[win[0]] != null && spaces[win[0]].equals("X") && spaces[win[1]] != null && spaces[win[1]].equals("X") && spaces[win[2]] != null && spaces[win[2]].equals("X")) {
                    firstSpot = win[0];
                    secondSpot = win[2];
                    won = true;
                    // Addition
                    if (serverSocket != null) {
                        restartJButton.setEnabled(true);
                    }
                    yourTurn = false;
                    // Addition
                }
            }
        }
    }

    private void checkForEnemyWin() {
        for (int[] win : wins) {
            if (circle) {
                if (spaces[win[0]] != null && spaces[win[0]].equals("X") && spaces[win[1]] != null && spaces[win[1]].equals("X") && spaces[win[2]] != null && spaces[win[2]].equals("X")) {
                    firstSpot = win[0];
                    secondSpot = win[2];
                    enemyWon = true;
                    // Addition
                    if (serverSocket != null) {
                        restartJButton.setEnabled(true);
                    }
                    yourTurn = false;
                    // Addition
                }
            } else {
                if (spaces[win[0]] != null && spaces[win[0]].equals("O") && spaces[win[1]] != null && spaces[win[1]].equals("O") && spaces[win[2]] != null && spaces[win[2]].equals("O")) {
                    firstSpot = win[0];
                    secondSpot = win[2];
                    enemyWon = true;
                    // Addition
                    if (serverSocket != null) {
                        restartJButton.setEnabled(true);
                    }
                    yourTurn = false;
                    // Addition
                }
            }
        }

    }

    private void checkForTie() {
        for (String space : spaces) {
            if (space == null) {
                return;
            }
        }
        tie = true;
    }

    private void listenForServerRequest() {
        Socket socket = null;
        try {
            socket = serverSocket.accept();// BLOCKS UNTIL SOMEONE CONNECTS
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            accepted = true;
            System.out.println("CLIENT HAS REQUESTED TO JOIN, AND WE HAVE ACCEPTED.");
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private boolean connect() {
        try {
            socket = new Socket(ip, port);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            accepted = true;
        } catch (IOException e) {
            System.out.println("Unable to connect to the address: " + ip + ":" + port + " | Starting a server.");
            return false;
        }
        System.out.println("Successfully connected to the server.");
        return true;
    }

    private void initializeServer() {
        try {
            serverSocket = new ServerSocket(port, 8, InetAddress.getByName(ip));
        } catch (Exception e) {
            e.printStackTrace();

        }
        yourTurn = true;
        circle = false;
    }


    private void loadImages() {
        try {
            board = ImageIO.read(getClass().getResourceAsStream("/board.png"));
            redX = ImageIO.read(getClass().getResourceAsStream("/redX.png"));
            redCircle = ImageIO.read(getClass().getResourceAsStream("/redCircle.png"));
            blueX = ImageIO.read(getClass().getResourceAsStream("/blueX.png"));
            blueCircle = ImageIO.read(getClass().getResourceAsStream("/blueCircle.png"));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        TicTacToe ticTacToe = new TicTacToe();
    }

    public class Painter extends JPanel implements MouseListener {
        private static final long serialVersionUID = 1L;

        public Painter() {
            setFocusable(true);
            requestFocus();
            setBackground(Color.white);
            addMouseListener(this);
        }
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            render(g);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (accepted) {
                if (yourTurn && !unableToCommunicateToOpponent && !won && !enemyWon) {
                    int x = e.getX() / lengthOfSpace;
                    int y = e.getY() / lengthOfSpace;
                    y *= 3;
                    int position = x + y;

                    if (spaces[position] == null) {
                        if (!circle) spaces[position] = "X";
                        else spaces[position] = "O";
                        yourTurn = false;
                        repaint();
                        Toolkit.getDefaultToolkit().sync();

                        try {
                            dos.writeInt(position);
                            dos.flush();
                        } catch (IOException e1) {
                            errors++;
                            e1.printStackTrace();
                        }

                        System.out.println("DATA WAS SENT");
                        checkForWin();
                        checkForTie();

                    }
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {

        }

        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mouseEntered(MouseEvent e) {

        }

        @Override
        public void mouseExited(MouseEvent e) {

        }
    }
}
