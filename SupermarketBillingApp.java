package supermarket;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.ArrayList;

public class SupermarketBillingApp extends JFrame {
    private Connection dbConnection;
    private JTextArea billingDisplay;
    private JTextArea receiptDisplay;

    public SupermarketBillingApp() {
        super("Supermarket Billing System");
        setupUI();
        establishConnection();
    }

    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, Color.decode("#6A82FB"), 0, getHeight(), Color.decode("#FC5C7D")));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        backgroundPanel.setLayout(new BorderLayout());
        add(backgroundPanel);

        JLabel titleLabel = new JLabel("Welcome to Kim's Convenience", JLabel.CENTER);
        titleLabel.setFont(new Font("Verdana", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        backgroundPanel.add(titleLabel, BorderLayout.NORTH);

        billingDisplay = new JTextArea();
        billingDisplay.setEditable(false);
        billingDisplay.setFont(new Font("Arial", Font.PLAIN, 16));
        billingDisplay.setLineWrap(true);
        billingDisplay.setWrapStyleWord(true);
        billingDisplay.setForeground(Color.BLACK);
        billingDisplay.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane billingPane = new JScrollPane(billingDisplay);
        billingPane.setPreferredSize(new Dimension(900, 300));
        billingPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                "Billing Details",
                0,
                0,
                new Font("Arial", Font.BOLD, 16),
                Color.WHITE
        ));
        backgroundPanel.add(billingPane, BorderLayout.CENTER);

        receiptDisplay = new JTextArea();
        receiptDisplay.setEditable(false);
        receiptDisplay.setFont(new Font("Courier New", Font.PLAIN, 14));
        receiptDisplay.setForeground(Color.BLACK);
        receiptDisplay.setBackground(Color.decode("#F5F5F5"));
        receiptDisplay.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane receiptPane = new JScrollPane(receiptDisplay);
        receiptPane.setPreferredSize(new Dimension(900, 250));
        receiptPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                "Final Bill",
                0,
                0,
                new Font("Arial", Font.BOLD, 16),
                Color.WHITE
        ));
        backgroundPanel.add(receiptPane, BorderLayout.SOUTH);

        showSplashScreen();
    }

    private void establishConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/dbms", "root", ""); // add your password here
        } catch (Exception ex) {
            billingDisplay.append("Error connecting to database: " + ex.getMessage() + "\n");
        }
    }

    private void showSplashScreen() {
        billingDisplay.setText("\t\n");
        Timer splashTimer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                promptPasscode();
            }
        });
        splashTimer.setRepeats(false);
        splashTimer.start();
    }

    private void promptPasscode() {
        String passcode = JOptionPane.showInputDialog(this, "Enter your 4-digit passcode:");
        if (passcode == null || passcode.length() != 4 || !passcode.matches("\\d+")) {
            JOptionPane.showMessageDialog(this, "Invalid passcode. Please enter a 4-digit number.");
            promptPasscode();
            return;
        }

        try (PreparedStatement ps = dbConnection.prepareStatement("SELECT CashierName FROM CashierInfo WHERE CashierID = ?")) {
            ps.setInt(1, Integer.parseInt(passcode));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                billingDisplay.append("Welcome, " + rs.getString("CashierName") + "!\n");
                beginTransaction();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid passcode. Please try again.");
                promptPasscode();
            }
        } catch (SQLException e) {
            billingDisplay.append("Error validating passcode: " + e.getMessage() + "\n");
        }
    }

    private void beginTransaction() {
        ArrayList<String> receipt = new ArrayList<>();
        double grossTotal = 0.0;

        try {
            PreparedStatement fetchItem = dbConnection.prepareStatement("SELECT * FROM Inventory WHERE ItemID = ?");
            PreparedStatement fetchDiscount = dbConnection.prepareStatement("SELECT * FROM Discount WHERE ItemID = ?");
            PreparedStatement updateStock = dbConnection.prepareStatement("UPDATE Inventory SET Stock = ? WHERE ItemID = ?");
            PreparedStatement fetchCustomer = dbConnection.prepareStatement("SELECT * FROM CustomerInfo WHERE CustomerNumber = ?");
            PreparedStatement updateCustomer = dbConnection.prepareStatement("UPDATE CustomerInfo SET RewardPoints = ? WHERE CustomerNumber = ?");

            String customerId = JOptionPane.showInputDialog("Enter Customer Number:");
            int rewardPoints = 0;
            String customerName = "";

            if (customerId != null && !customerId.isEmpty()) {
                fetchCustomer.setString(1, customerId);
                ResultSet customerRs = fetchCustomer.executeQuery();
                if (customerRs.next()) {
                    rewardPoints = customerRs.getInt("RewardPoints");
                    customerName = customerRs.getString("CustomerName");
                    billingDisplay.append("Customer: " + customerName + " | Reward Points: " + rewardPoints + "\n");
                } else {
                    JOptionPane.showMessageDialog(this, "Customer not found. Continuing without reward points.");
                }
            }

            while (true) {
                String itemId = JOptionPane.showInputDialog("Enter Item ID (or 'done' to finish):");
                if (itemId == null || itemId.equalsIgnoreCase("done")) break;

                fetchItem.setString(1, itemId);
                ResultSet itemRs = fetchItem.executeQuery();
                if (!itemRs.next()) {
                    billingDisplay.append("Item not found. Please try again.\n");
                    continue;
                }

                String itemName = itemRs.getString("ItemName");
                int stock = itemRs.getInt("Stock");

                fetchDiscount.setString(1, itemId);
                ResultSet discountRs = fetchDiscount.executeQuery();
                double price = 0.0;
                double discount = 0.0;
                if (discountRs.next()) {
                    price = discountRs.getDouble("Price");
                    discount = discountRs.getDouble("Discount");
                }

                billingDisplay.append(String.format("Item: %s | Price: %.2f | Stock: %d\n", itemName, price, stock));

                String qtyStr = JOptionPane.showInputDialog("Enter quantity:");
                if (qtyStr == null || !qtyStr.matches("\\d+")) continue;
                int quantity = Integer.parseInt(qtyStr);

                if (quantity > stock) {
                    JOptionPane.showMessageDialog(this, String.format("Not enough stock for %s. Available: %d", itemName, stock));
                    billingDisplay.append("Insufficient stock for " + itemName + ". Available: " + stock + "\n");
                    continue;
                }

                double itemTotal = quantity * price * (1 - discount / 100.0);
                grossTotal += itemTotal;
                stock -= quantity;

                updateStock.setInt(1, stock);
                updateStock.setString(2, itemId);
                updateStock.executeUpdate();

                receipt.add(String.format("%s\t%s\t%.2f\t%d\t%.2f", itemId, itemName, price, quantity, itemTotal));
                billingDisplay.append(String.format("Scanned: %s | Quantity: %d | Remaining Stock: %d | Total: %.2f\n", itemName, quantity, stock, itemTotal));
            }

            grossTotal = applyRewards(grossTotal, rewardPoints);

            int newPoints = (int) (grossTotal / 100);
            billingDisplay.append("New Reward Points Earned: " + newPoints + "\n");

            if (customerId != null && !customerId.isEmpty()) {
                int updatedPoints = Math.max(0, rewardPoints - (int) grossTotal + newPoints);
                updateCustomer.setInt(1, updatedPoints);
                updateCustomer.setString(2, customerId);
                updateCustomer.executeUpdate();
            }

            showReceipt(receipt, customerName, customerId, grossTotal);

        } catch (SQLException e) {
            billingDisplay.append("Billing error: " + e.getMessage() + "\n");
        }
    }

    private double applyRewards(double gross, int rewardPoints) {
        billingDisplay.append(String.format("Gross Amount Before Rewards: %.2f\n", gross));
        int redeemable = Math.min(rewardPoints, (int) gross);
        gross -= redeemable;
        billingDisplay.append("Reward Points Redeemed: " + redeemable + "\n");
        return gross;
    }

    private void showReceipt(ArrayList<String> receipt, String customerName, String customerNumber, double gross) {
        JDialog dialog = new JDialog(this, "Final Bill", true);
        dialog.setSize(600, 800);
        dialog.setLocationRelativeTo(this);

        JTextArea receiptArea = new JTextArea();
        receiptArea.setEditable(false);
        receiptArea.setFont(new Font("Courier New", Font.PLAIN, 14));

        receiptArea.setText("----- Kim's Convenience -----\n");
        receiptArea.append("Customer: " + customerName + " | Customer No: " + customerNumber + "\n");
        receiptArea.append("------------------------------------------\n");
        receiptArea.append("ItemID  ItemName  Price  Quantity  Total\n");
        receiptArea.append("------------------------------------------\n");

        for (String line : receipt) {
            receiptArea.append(line + "\n");
        }

        double cgst = gross * 0.025;
        double sgst = gross * 0.025;
        double netTotal = gross + cgst + sgst;

        receiptArea.append("------------------------------------------\n");
        receiptArea.append(String.format("Gross: %.2f\nCGST: %.2f\nSGST: %.2f\nNet: %.2f\n", gross, cgst, sgst, netTotal));
        receiptArea.append("\nThank you for shopping at Kim's Convenience!\n");

        JScrollPane scroll = new JScrollPane(receiptArea);
        dialog.add(scroll, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel panel = new JPanel();
        panel.add(close);
        dialog.add(panel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SupermarketBillingApp().setVisible(true));
    }
}
