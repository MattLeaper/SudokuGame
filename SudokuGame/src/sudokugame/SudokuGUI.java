package sudokugame;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.Stack;

public class SudokuGUI extends JFrame {

    private static final int SIZE = 9;
    private JTextField[][] cells = new JTextField[SIZE][SIZE];
    private int[][] solution = new int[SIZE][SIZE];
    private int[][] puzzle = new int[SIZE][SIZE];

    private Stack<Move> undoStack = new Stack<>();
    private Timer timer;
    private int elapsedSeconds = 0;
    private JLabel timerLabel = new JLabel("Time: 0s");

    public SudokuGUI() {
        setTitle("Sudoku Game");
        setSize(650, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel boardPanel = new JPanel(new GridLayout(SIZE, SIZE));
        boardPanel.setBorder(new LineBorder(Color.BLACK, 2));

        Font font = new Font("SansSerif", Font.BOLD, 20);

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                JTextField cell = new JTextField();
                cell.setHorizontalAlignment(JTextField.CENTER);
                cell.setFont(font);
                cell.setBorder(new LineBorder(Color.GRAY));

                // Thicker border for 3x3 boxes
                int top = 1, left = 1, bottom = 1, right = 1;
                if (row % 3 == 0) top = 3;
                if (col % 3 == 0) left = 3;
                if (row == SIZE - 1) bottom = 3;
                if (col == SIZE - 1) right = 3;
                cell.setBorder(BorderFactory.createMatteBorder(top, left, bottom, right, Color.BLACK));

                // Limit input to digits 1-9 and listen for undo & error highlight
                final int r = row, c = col;
                cell.addKeyListener(new KeyAdapter() {
                    public void keyPressed(KeyEvent e) {
                        // Before change, push old value for undo
                        if (e.getKeyCode() != KeyEvent.VK_UNDO) {
                            undoStack.push(new Move(r, c, cell.getText()));
                        }
                    }

                    public void keyTyped(KeyEvent e) {
                        char ch = e.getKeyChar();
                        if (!(ch >= '1' && ch <= '9') && ch != KeyEvent.VK_BACK_SPACE && ch != KeyEvent.VK_DELETE) {
                            e.consume(); // Ignore invalid input
                        }
                    }
                });

                cell.getDocument().addDocumentListener(new DocumentListener() {
                    private void validateCell() {
                        String text = cell.getText();
                        if (text.length() == 1) {
                            char ch = text.charAt(0);
                            if (ch >= '1' && ch <= '9') {
                                int num = Integer.parseInt(text);
                                // Temporarily put num in puzzle copy for validation
                                int oldVal = puzzle[r][c];
                                puzzle[r][c] = num;
                                if (!isValidPlacement(puzzle, r, c, num)) {
                                    cell.setForeground(Color.RED);
                                } else {
                                    cell.setForeground(Color.BLACK);
                                }
                                puzzle[r][c] = oldVal; // restore old value
                            } else {
                                cell.setForeground(Color.BLACK);
                            }
                        } else {
                            cell.setForeground(Color.BLACK);
                        }
                    }

                    @Override public void insertUpdate(DocumentEvent e) { validateCell(); }
                    @Override public void removeUpdate(DocumentEvent e) { validateCell(); }
                    @Override public void changedUpdate(DocumentEvent e) { validateCell(); }
                });

                cells[row][col] = cell;
                boardPanel.add(cell);
            }
        }

        // Difficulty selection
        String[] difficulties = {"Easy", "Medium", "Hard"};
        JComboBox<String> difficultyCombo = new JComboBox<>(difficulties);

        JButton newGameButton = new JButton("New Game");
        newGameButton.addActionListener(e -> {
            elapsedSeconds = 0;
            timerLabel.setText("Time: 0s");
            generatePuzzle((String) difficultyCombo.getSelectedItem());
            loadPuzzleToGrid();
            undoStack.clear();
            timer.restart();
        });

        JButton checkButton = new JButton("Check Solution");
        checkButton.addActionListener(e -> checkSolution());

        JButton undoButton = new JButton("Undo");
        undoButton.addActionListener(e -> {
            if (!undoStack.isEmpty()) {
                Move last = undoStack.pop();
                JTextField cell = cells[last.row][last.col];
                cell.setText(last.prevValue);
            }
        });

        JButton hintButton = new JButton("Hint");
        hintButton.addActionListener(e -> giveHint());

        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("Difficulty:"));
        controlPanel.add(difficultyCombo);
        controlPanel.add(newGameButton);
        controlPanel.add(checkButton);
        controlPanel.add(undoButton);
        controlPanel.add(hintButton);
        controlPanel.add(timerLabel);

        add(boardPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        // Timer setup
        timer = new Timer(1000, e -> {
            elapsedSeconds++;
            timerLabel.setText("Time: " + elapsedSeconds + "s");
        });
        timer.start();

        // Start with an easy puzzle
        generatePuzzle("Easy");
        loadPuzzleToGrid();
    }

    // Load puzzle numbers into text fields
    private void loadPuzzleToGrid() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int val = puzzle[row][col];
                JTextField cell = cells[row][col];
                if (val != 0) {
                    cell.setText(String.valueOf(val));
                    cell.setEditable(false);
                    cell.setForeground(Color.BLUE);
                } else {
                    cell.setText("");
                    cell.setEditable(true);
                    cell.setForeground(Color.BLACK);
                }
            }
        }
    }

    // Generate a puzzle by:
    // 1. Creating a full solved board
    // 2. Removing numbers depending on difficulty
    private void generatePuzzle(String difficulty) {
        generateFullSolution();
        puzzle = copyBoard(solution);

        int removeCount;
        switch (difficulty.toLowerCase()) {
            case "medium": removeCount = 40; break;
            case "hard": removeCount = 50; break;
            default: removeCount = 30; // easy
        }

        Random rand = new Random();
        int removed = 0;
        while (removed < removeCount) {
            int r = rand.nextInt(SIZE);
            int c = rand.nextInt(SIZE);
            if (puzzle[r][c] != 0) {
                puzzle[r][c] = 0;
                removed++;
            }
        }
    }

    // Generate a full valid Sudoku solution (backtracking)
    private boolean generateFullSolution() {
        solution = new int[SIZE][SIZE];
        return fillBoard(0, 0);
    }

    private boolean fillBoard(int row, int col) {
        if (row == SIZE) return true;
        if (col == SIZE) return fillBoard(row + 1, 0);

        Random rand = new Random();
        int[] numbers = new int[SIZE];
        for (int i = 0; i < SIZE; i++) numbers[i] = i + 1;

        // Shuffle numbers for randomness
        for (int i = SIZE - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int temp = numbers[i];
            numbers[i] = numbers[j];
            numbers[j] = temp;
        }

        for (int num : numbers) {
            if (isValidPlacement(solution, row, col, num)) {
                solution[row][col] = num;
                if (fillBoard(row, col + 1)) return true;
                solution[row][col] = 0;
            }
        }
        return false;
    }

    private boolean isValidPlacement(int[][] board, int row, int col, int num) {
        for (int i = 0; i < SIZE; i++) {
            if (board[row][i] == num) return false;
            if (board[i][col] == num) return false;
        }
        int boxRowStart = (row / 3) * 3;
        int boxColStart = (col / 3) * 3;
        for (int r = boxRowStart; r < boxRowStart + 3; r++) {
            for (int c = boxColStart; c < boxColStart + 3; c++) {
                if (board[r][c] == num) return false;
            }
        }
        return true;
    }

    // Deep copy of a board
    private int[][] copyBoard(int[][] original) {
        int[][] copy = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            System.arraycopy(original[i], 0, copy[i], 0, SIZE);
        return copy;
    }

    // Check if the user's solution is correct
    private void checkSolution() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                String text = cells[row][col].getText();
                if (text.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "The board is not complete!", "Incomplete", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int val;
                try {
                    val = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Invalid input detected!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (val != solution[row][col]) {
                    JOptionPane.showMessageDialog(this, "Incorrect solution. Keep trying!", "Try Again", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }
        timer.stop();
        JOptionPane.showMessageDialog(this, "Congratulations! You solved the puzzle in " + elapsedSeconds + " seconds.", "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // Provide a hint by filling one empty or incorrect cell
    private void giveHint() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                String text = cells[row][col].getText();
                int val = 0;
                try {
                    val = Integer.parseInt(text);
                } catch (NumberFormatException ignored) {}
                if (text.isEmpty() || val != solution[row][col]) {
                    cells[row][col].setText(String.valueOf(solution[row][col]));
                    cells[row][col].setEditable(false);
                    cells[row][col].setForeground(Color.MAGENTA);
                    return; // only one hint at a time
                }
            }
        }
        JOptionPane.showMessageDialog(this, "No hints available. Puzzle might be solved!", "Hint", JOptionPane.INFORMATION_MESSAGE);
    }

    private static class Move {
        int row, col;
        String prevValue;
        Move(int r, int c, String v) {
            row = r;
            col = c;
            prevValue = v;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SudokuGUI gui = new SudokuGUI();
            gui.setVisible(true);
        });
    }
}
