import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** FINDIT - Intelligent Campus Lost & Found System (Java Swing, Collections, File Handling, OOP). */
public class FindIt extends JFrame {

    static final Color NAVY = new Color(0x001440), PANEL = new Color(0x0A2457),
            BLUE = new Color(0x7CC7EE), GREEN = new Color(0xB5E6A2), ORANGE = new Color(0xF07030);
    static final String[] CATEGORIES = {"Bag", "Phone", "Laptop", "Wallet", "ID Card", "Keys", "Books",
            "Bottle", "Earphones", "Clothing", "Stationery", "Other"};
    static final String[] COLOURS = {"Black", "White", "Blue", "Red", "Green", "Yellow", "Grey", "Brown",
            "Pink", "Orange", "Purple", "Multicolour"};
    static final int MATCH_THRESHOLD = 55;
    static final Path DATA_FILE = Paths.get("findit_data.txt");

    // ---------- Model ----------
    static class Item {
        String id, type, name, category, colour, location, date, contact, desc, status;

        Item(String id, String type, String name, String category, String colour, String location,
             String date, String contact, String desc, String status) {
            this.id = id; this.type = type; this.name = name; this.category = category;
            this.colour = colour; this.location = location; this.date = date;
            this.contact = contact; this.desc = desc; this.status = status;
        }

        boolean isOpen() { return "OPEN".equals(status); }

        LocalDate day() {
            try { return LocalDate.parse(date); } catch (Exception e) { return null; }
        }

        String toLine() {
            String[] f = {id, type, name, category, colour, location, date, contact, desc, status};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < f.length; i++) { if (i > 0) sb.append('\t'); sb.append(esc(f[i])); }
            return sb.toString();
        }

        static Item fromLine(String line) {
            String[] p = line.split("\t", -1);
            if (p.length < 10) return null;
            for (int i = 0; i < p.length; i++) p[i] = unesc(p[i]);
            return new Item(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9]);
        }

        static String esc(String s) {
            return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
        }

        static String unesc(String s) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(++i);
                    sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
                } else sb.append(c);
            }
            return sb.toString();
        }
    }

    // ---------- Matching ----------
    static int score(Item lost, Item found) {
        if (!lost.category.equalsIgnoreCase(found.category)) return 0;
        int s = 35;
        if (lost.colour.equalsIgnoreCase(found.colour)) s += 25;
        String a = lost.location.toLowerCase().trim(), b = found.location.toLowerCase().trim();
        if (!a.isEmpty() && !b.isEmpty()) {
            if (a.equals(b)) s += 20;
            else if (a.contains(b) || b.contains(a)) s += 12;
        }
        LocalDate d1 = lost.day(), d2 = found.day();
        if (d1 != null && d2 != null) {
            long diff = Math.abs(ChronoUnit.DAYS.between(d1, d2));
            if (diff <= 1) s += 20; else if (diff <= 3) s += 14; else if (diff <= 7) s += 8;
        }
        for (String w : lost.name.toLowerCase().split("\\W+"))
            if (w.length() > 2 && found.name.toLowerCase().contains(w)) { s += 20; break; }
        return Math.min(100, s);
    }

    // ---------- State ----------
    final List<Item> items = new ArrayList<>();
    final List<Item[]> pairs = new ArrayList<>();
    DefaultTableModel browseModel, matchModel;
    JTable browseTable, matchTable;
    JTextField searchField;
    JComboBox<String> typeFilter, catFilter, statusFilter;
    JLabel statsLabel;

    // Report form
    JRadioButton lostBtn, foundBtn;
    JTextField nameF, locF, dateF, contactF;
    JComboBox<String> catC, colC;
    JTextArea descA;

    final User user;

    public FindIt(User user) {
        super("FINDIT - Intelligent Campus Lost & Found System");
        this.user = user;
        load();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1050, 680);
        setLocationRelativeTo(null);
        getContentPane().setBackground(NAVY);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 8, 18));
        JLabel title = new JLabel("FINDIT \uD83D\uDCCD  Intelligent Campus Lost & Found System");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(BLUE);
        statsLabel = new JLabel();
        statsLabel.setForeground(GREEN);
        header.add(title, BorderLayout.WEST);
        JPanel right = new JPanel(new GridLayout(3, 1, 0, 2));
        right.setOpaque(false);
        JLabel who = new JLabel(user.name + " | " + user.reg + " | " + user.course + " " + user.branch
                + " (" + user.spec + ") Sec " + user.section);
        who.setForeground(BLUE);
        JButton out = btn("Logout");
        out.addActionListener(e -> { dispose(); new AuthFrame().setVisible(true); });
        right.add(statsLabel); right.add(who); right.add(out);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Report Item", buildReportPanel());
        tabs.addTab("Search & Records", buildBrowsePanel());
        tabs.addTab("Smart Matches", buildMatchPanel());
        tabs.addChangeListener(e -> refreshAll());
        add(tabs, BorderLayout.CENTER);
        contactF.setText(user.phone);
        refreshAll();
    }

    // ---------- UI builders ----------
    static JLabel lbl(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(GREEN);
        return l;
    }

    static JButton btn(String t) {
        JButton b = new JButton(t);
        b.setBackground(ORANGE);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return b;
    }

    JPanel buildReportPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        lostBtn = new JRadioButton("I LOST an item", true);
        foundBtn = new JRadioButton("I FOUND an item");
        for (JRadioButton r : new JRadioButton[]{lostBtn, foundBtn}) {
            r.setOpaque(false); r.setForeground(BLUE);
        }
        ButtonGroup bg = new ButtonGroup();
        bg.add(lostBtn); bg.add(foundBtn);
        JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        radios.setOpaque(false);
        radios.add(lostBtn); radios.add(foundBtn);

        nameF = new JTextField(24);
        catC = new JComboBox<>(CATEGORIES);
        colC = new JComboBox<>(COLOURS);
        colC.setEditable(true);
        locF = new JTextField(24);
        dateF = new JTextField(LocalDate.now().toString(), 24);
        contactF = new JTextField(24);
        descA = new JTextArea(4, 24);
        descA.setLineWrap(true);
        descA.setWrapStyleWord(true);

        String[] labels = {"Report type", "Item name *", "Category *", "Colour *", "Location *",
                "Date (YYYY-MM-DD) *", "Contact (phone/email) *", "Description"};
        Component[] fields = {radios, nameF, catC, colC, locF, dateF, contactF, new JScrollPane(descA)};
        for (int i = 0; i < labels.length; i++) {
            g.gridx = 0; g.gridy = i; g.weightx = 0;
            p.add(lbl(labels[i]), g);
            g.gridx = 1; g.weightx = 1;
            p.add(fields[i], g);
        }
        JButton submit = btn("Submit Report");
        submit.addActionListener(e -> submitReport());
        g.gridx = 1; g.gridy = labels.length; g.fill = GridBagConstraints.NONE;
        p.add(submit, g);
        return p;
    }

    JPanel buildBrowsePanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.setOpaque(false);
        searchField = new JTextField(18);
        typeFilter = new JComboBox<>(new String[]{"All", "LOST", "FOUND"});
        catFilter = new JComboBox<>(prepend("All", CATEGORIES));
        statusFilter = new JComboBox<>(new String[]{"All", "OPEN", "RECOVERED"});
        top.add(lbl("Search:")); top.add(searchField);
        top.add(lbl("Type:")); top.add(typeFilter);
        top.add(lbl("Category:")); top.add(catFilter);
        top.add(lbl("Status:")); top.add(statusFilter);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshBrowse(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshBrowse(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshBrowse(); }
        });
        typeFilter.addActionListener(e -> refreshBrowse());
        catFilter.addActionListener(e -> refreshBrowse());
        statusFilter.addActionListener(e -> refreshBrowse());

        browseModel = new DefaultTableModel(new String[]{"ID", "Type", "Item", "Category", "Colour",
                "Location", "Date", "Contact", "Status"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        browseTable = new JTable(browseModel);
        browseTable.setRowHeight(24);
        browseTable.setAutoCreateRowSorter(true);

        JButton recovered = btn("Mark Recovered");
        recovered.addActionListener(e -> {
            Item it = selectedItem();
            if (it == null) return;
            it.status = "RECOVERED"; save(); refreshAll();
        });
        JButton details = btn("View Details");
        details.addActionListener(e -> {
            Item it = selectedItem();
            if (it == null) return;
            JOptionPane.showMessageDialog(this,
                    it.type + " - " + it.name + "\nCategory: " + it.category + "\nColour: " + it.colour
                            + "\nLocation: " + it.location + "\nDate: " + it.date + "\nContact: " + it.contact
                            + "\nStatus: " + it.status + "\n\nDescription:\n" + (it.desc.isEmpty() ? "-" : it.desc),
                    "Item " + it.id, JOptionPane.INFORMATION_MESSAGE);
        });
        JButton delete = btn("Delete");
        delete.addActionListener(e -> {
            Item it = selectedItem();
            if (it == null) return;
            if (JOptionPane.showConfirmDialog(this, "Delete record " + it.id + "?", "Confirm",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                items.remove(it); save(); refreshAll();
            }
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottom.setOpaque(false);
        bottom.add(details); bottom.add(recovered); bottom.add(delete);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(browseTable), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    JPanel buildMatchPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel info = lbl("Open LOST and FOUND reports compared on category, colour, location, date and name (min score "
                + MATCH_THRESHOLD + "%).");
        matchModel = new DefaultTableModel(new String[]{"Match %", "Lost Item", "Owner Contact", "Found Item",
                "Found At", "Finder Contact"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        matchTable = new JTable(matchModel);
        matchTable.setRowHeight(24);
        JButton done = btn("Mark Selected Pair Recovered");
        done.addActionListener(e -> {
            int r = matchTable.getSelectedRow();
            if (r < 0) { JOptionPane.showMessageDialog(this, "Select a match first."); return; }
            pairs.get(r)[0].status = "RECOVERED";
            pairs.get(r)[1].status = "RECOVERED";
            save(); refreshAll();
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setOpaque(false);
        bottom.add(done);
        p.add(info, BorderLayout.NORTH);
        p.add(new JScrollPane(matchTable), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    // ---------- Actions ----------
    void submitReport() {
        String name = nameF.getText().trim(), loc = locF.getText().trim(),
                contact = contactF.getText().trim(), date = dateF.getText().trim();
        String colour = String.valueOf(colC.getSelectedItem()).trim();
        if (name.isEmpty() || loc.isEmpty() || contact.isEmpty() || colour.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields marked with *.", "Missing data",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try { LocalDate.parse(date); } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Date must be in YYYY-MM-DD format.", "Invalid date",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Item it = new Item(String.valueOf(nextId()), lostBtn.isSelected() ? "LOST" : "FOUND", name,
                (String) catC.getSelectedItem(), colour, loc, date, contact, descA.getText().trim(), "OPEN");
        items.add(it);
        save();

        List<String> hits = new ArrayList<>();
        for (Item o : items) {
            if (!o.isOpen() || o.type.equals(it.type)) continue;
            int s = it.type.equals("LOST") ? score(it, o) : score(o, it);
            if (s >= MATCH_THRESHOLD) hits.add(s + "%  " + o.type + ": " + o.name + " (" + o.location + ") - " + o.contact);
        }
        String msg = "Report saved (ID " + it.id + ").";
        if (!hits.isEmpty())
            msg += "\n\nPotential matches found:\n" + String.join("\n", hits.stream().limit(5).collect(Collectors.toList()));
        JOptionPane.showMessageDialog(this, msg, "FINDIT", JOptionPane.INFORMATION_MESSAGE);

        nameF.setText(""); locF.setText(""); contactF.setText(""); descA.setText("");
        dateF.setText(LocalDate.now().toString());
        refreshAll();
    }

    Item selectedItem() {
        int r = browseTable.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Select a record first."); return null; }
        String id = (String) browseModel.getValueAt(browseTable.convertRowIndexToModel(r), 0);
        return items.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
    }

    // ---------- Refresh ----------
    void refreshAll() {
        refreshBrowse();
        refreshMatches();
        long lost = items.stream().filter(i -> i.type.equals("LOST") && i.isOpen()).count();
        long found = items.stream().filter(i -> i.type.equals("FOUND") && i.isOpen()).count();
        long rec = items.stream().filter(i -> !i.isOpen()).count();
        statsLabel.setText("Open lost: " + lost + "   Open found: " + found + "   Recovered: " + rec);
    }

    void refreshBrowse() {
        if (browseModel == null) return;
        browseModel.setRowCount(0);
        String q = searchField.getText().trim().toLowerCase();
        String t = (String) typeFilter.getSelectedItem(), c = (String) catFilter.getSelectedItem(),
                s = (String) statusFilter.getSelectedItem();
        for (Item i : items) {
            if (!t.equals("All") && !i.type.equals(t)) continue;
            if (!c.equals("All") && !i.category.equals(c)) continue;
            if (!s.equals("All") && !i.status.equals(s)) continue;
            String hay = (i.name + " " + i.colour + " " + i.location + " " + i.desc + " " + i.category).toLowerCase();
            if (!q.isEmpty() && !hay.contains(q)) continue;
            browseModel.addRow(new Object[]{i.id, i.type, i.name, i.category, i.colour, i.location, i.date,
                    i.contact, i.status});
        }
    }

    void refreshMatches() {
        if (matchModel == null) return;
        pairs.clear();
        List<Object[]> rows = new ArrayList<>();
        List<int[]> order = new ArrayList<>();
        List<Item[]> tmp = new ArrayList<>();
        for (Item l : items) {
            if (!l.type.equals("LOST") || !l.isOpen()) continue;
            for (Item f : items) {
                if (!f.type.equals("FOUND") || !f.isOpen()) continue;
                int s = score(l, f);
                if (s >= MATCH_THRESHOLD) {
                    tmp.add(new Item[]{l, f});
                    order.add(new int[]{s, tmp.size() - 1});
                }
            }
        }
        order.sort((a, b) -> b[0] - a[0]);
        matchModel.setRowCount(0);
        for (int[] o : order) {
            Item[] pr = tmp.get(o[1]);
            pairs.add(pr);
            matchModel.addRow(new Object[]{o[0] + "%", pr[0].name + " (" + pr[0].colour + ")", pr[0].contact,
                    pr[1].name + " (" + pr[1].colour + ")", pr[1].location, pr[1].contact});
        }
    }

    // ---------- File handling ----------
    int nextId() {
        int max = 0;
        for (Item i : items) try { max = Math.max(max, Integer.parseInt(i.id)); } catch (NumberFormatException ignored) { }
        return max + 1;
    }

    void load() {
        if (!Files.exists(DATA_FILE)) return;
        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                Item it = Item.fromLine(line);
                if (it != null) items.add(it);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not read data file: " + e.getMessage());
        }
    }

    void save() {
        try {
            Files.write(DATA_FILE, items.stream().map(Item::toLine).collect(Collectors.toList()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save data: " + e.getMessage());
        }
    }

    static String[] prepend(String first, String[] rest) {
        String[] r = new String[rest.length + 1];
        r[0] = first;
        System.arraycopy(rest, 0, r, 1, rest.length);
        return r;
    }

    // ---------- Users (registration & login) ----------
    static final Path USER_FILE = Paths.get("findit_users.txt");

    static class User {
        String username, phone, salt, hash, name, reg, course, branch, spec, section;

        String toLine() {
            String[] f = {username, phone, salt, hash, name, reg, course, branch, spec, section};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < f.length; i++) { if (i > 0) sb.append('\t'); sb.append(Item.esc(f[i])); }
            return sb.toString();
        }

        static User fromLine(String line) {
            String[] p = line.split("\t", -1);
            if (p.length < 10) return null;
            for (int i = 0; i < p.length; i++) p[i] = Item.unesc(p[i]);
            User u = new User();
            u.username = p[0]; u.phone = p[1]; u.salt = p[2]; u.hash = p[3]; u.name = p[4];
            u.reg = p[5]; u.course = p[6]; u.branch = p[7]; u.spec = p[8]; u.section = p[9];
            return u;
        }
    }

    static List<User> loadUsers() {
        List<User> list = new ArrayList<>();
        if (!Files.exists(USER_FILE)) return list;
        try {
            for (String line : Files.readAllLines(USER_FILE, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                User u = User.fromLine(line);
                if (u != null) list.add(u);
            }
        } catch (IOException ignored) { }
        return list;
    }

    static void saveUsers(List<User> users) {
        try {
            Files.write(USER_FILE, users.stream().map(User::toLine).collect(Collectors.toList()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not save users: " + e.getMessage());
        }
    }

    static String hash(String salt, String pw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + pw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** First screen: Login, or Register (phone -> OTP -> username/password -> details). */
    static class AuthFrame extends JFrame {
        final List<User> users = loadUsers();
        final CardLayout cards = new CardLayout();
        final JPanel deck = new JPanel(cards);
        final SecureRandom rnd = new SecureRandom();
        final JTextField lu = new JTextField(20), ph = new JTextField(20), otpF = new JTextField(20),
                ru = new JTextField(20), dn = new JTextField(20), dr = new JTextField(20);
        final JPasswordField lp = new JPasswordField(20), rp = new JPasswordField(20), rp2 = new JPasswordField(20);
        final JComboBox<String> dc = new JComboBox<>(new String[]{"B.Tech", "M.Tech", "BCA", "MCA", "B.Sc", "MBA"}),
                db = new JComboBox<>(new String[]{"C.S.E", "I.T", "E.C.E", "E.E.E", "Mechanical", "Civil", "Biotechnology"}),
                ds = new JComboBox<>(new String[]{"Data Science", "Artificial Intelligence", "Cyber Security",
                        "Cloud Computing", "IoT", "Core"}),
                dsec = new JComboBox<>(new String[]{"A", "B", "C", "D", "E", "F", "G", "H"});
        String phone, otp, username, password;
        long otpExp;
        int tries;

        AuthFrame() {
            super("FINDIT - Register / Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(520, 660);
            setLocationRelativeTo(null);
            getContentPane().setBackground(NAVY);
            for (JComboBox<String> c : List.of(dc, db, ds, dsec)) c.setEditable(true);

            JButton login = btn("Login");
            login.addActionListener(e -> doLogin());
            JButton toReg = btn("Register yourself");
            toReg.addActionListener(e -> cards.show(deck, "phone"));
            deck.add(form("FINDIT \uD83D\uDCCD Login", null, new String[]{"Username", "Password"},
                    new JComponent[]{lu, lp}, login, toReg), "login");

            JButton send = btn("Send OTP");
            send.addActionListener(e -> sendOtp());
            deck.add(form("Step 1 of 4 - Phone number", "Enter your 10-digit mobile number.",
                    new String[]{"Mobile number"}, new JComponent[]{ph}, send, backBtn()), "phone");

            JButton verify = btn("Verify OTP");
            verify.addActionListener(e -> verifyOtp());
            JButton resend = btn("Resend OTP");
            resend.addActionListener(e -> newOtp());
            deck.add(form("Step 2 of 4 - Enter OTP", "Enter the 6-digit OTP.",
                    new String[]{"OTP"}, new JComponent[]{otpF}, verify, resend), "otp");

            JButton next = btn("Next");
            next.addActionListener(e -> saveCreds());
            deck.add(form("Step 3 of 4 - Username & password",
                    "Username: 4-20 letters, numbers or _. Password: at least 6 characters.",
                    new String[]{"Username", "Password", "Confirm password"}, new JComponent[]{ru, rp, rp2}, next),
                    "creds");

            JButton fin = btn("Create account");
            fin.addActionListener(e -> finish());
            deck.add(form("Step 4 of 4 - Your details", null,
                    new String[]{"Full name", "Registration no.", "Course", "Branch", "Specialization", "Section"},
                    new JComponent[]{dn, dr, dc, db, ds, dsec}, fin), "details");

            add(deck);
            cards.show(deck, "login");
        }

        JButton backBtn() {
            JButton b = btn("Back to login");
            b.addActionListener(e -> cards.show(deck, "login"));
            return b;
        }

        static JPanel form(String title, String note, String[] labels, JComponent[] fields, JButton... buttons) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(PANEL);
            p.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = 0; g.weightx = 1;
            g.insets = new Insets(5, 0, 5, 0);
            g.fill = GridBagConstraints.HORIZONTAL;
            JLabel t = new JLabel(title);
            t.setFont(new Font("SansSerif", Font.BOLD, 20));
            t.setForeground(BLUE);
            p.add(t, g);
            if (note != null) {
                g.gridy++;
                p.add(lbl("<html>" + note + "</html>"), g);
            }
            for (int i = 0; i < labels.length; i++) {
                g.gridy++; p.add(lbl(labels[i]), g);
                g.gridy++; p.add(fields[i], g);
            }
            for (JButton b : buttons) { g.gridy++; p.add(b, g); }
            g.gridy++; g.weighty = 1;
            p.add(Box.createVerticalGlue(), g);
            return p;
        }

        void doLogin() {
            String u = lu.getText().trim().toLowerCase();
            String pw = new String(lp.getPassword());
            for (User x : users)
                if (x.username.equals(u) && x.hash.equals(hash(x.salt, pw))) { open(x); return; }
            err("Wrong username or password.");
        }

        void sendOtp() {
            String p = ph.getText().trim();
            if (!p.matches("[6-9]\\d{9}")) { err("Enter a valid 10-digit mobile number."); return; }
            for (User x : users)
                if (x.phone.equals(p)) { err("This number is already registered. Please login."); return; }
            phone = p;
            cards.show(deck, "otp");
            newOtp();
        }

        void newOtp() {
            otp = String.valueOf(100000 + rnd.nextInt(900000));
            otpExp = System.currentTimeMillis() + 300000;
            tries = 0;
            otpF.setText("");
            JOptionPane.showMessageDialog(this, "DEMO MODE: a desktop app cannot send real SMS.\nYour OTP for "
                    + phone + " is: " + otp + "\n(valid for 5 minutes)", "OTP", JOptionPane.INFORMATION_MESSAGE);
        }

        void verifyOtp() {
            if (otp == null || System.currentTimeMillis() > otpExp) { err("OTP expired. Click Resend OTP."); return; }
            if (++tries > 5) { otp = null; err("Too many wrong attempts. Click Resend OTP."); return; }
            if (!otpF.getText().trim().equals(otp)) { err("Wrong OTP. Try again."); return; }
            otp = null;
            cards.show(deck, "creds");
        }

        void saveCreds() {
            String u = ru.getText().trim().toLowerCase();
            String p = new String(rp.getPassword());
            if (!u.matches("[a-z0-9_]{4,20}")) { err("Username must be 4-20 letters, numbers or _."); return; }
            for (User x : users) if (x.username.equals(u)) { err("That username is taken."); return; }
            if (p.length() < 6) { err("Password must be at least 6 characters."); return; }
            if (!p.equals(new String(rp2.getPassword()))) { err("Passwords do not match."); return; }
            username = u;
            password = p;
            cards.show(deck, "details");
        }

        static String val(JComboBox<String> c) {
            Object o = c.getEditor().getItem();
            return o == null ? "" : o.toString().trim();
        }

        void finish() {
            String name = dn.getText().trim(), reg = dr.getText().trim().toUpperCase();
            String course = val(dc), branch = val(db), spec = val(ds), sec = val(dsec).toUpperCase();
            if (name.isEmpty() || reg.isEmpty() || course.isEmpty() || branch.isEmpty() || spec.isEmpty()
                    || sec.isEmpty()) { err("Please fill all details."); return; }
            if (!reg.matches("[A-Z0-9]{8,20}")) { err("Registration no. should be 8-20 letters/numbers."); return; }
            for (User x : users) if (x.reg.equals(reg)) { err("This registration no. is already registered."); return; }
            byte[] sb = new byte[16];
            rnd.nextBytes(sb);
            User u = new User();
            u.salt = new java.math.BigInteger(1, sb).toString(16);
            u.username = username; u.phone = phone; u.hash = hash(u.salt, password);
            u.name = name; u.reg = reg; u.course = course; u.branch = branch; u.spec = spec; u.section = sec;
            users.add(u);
            saveUsers(users);
            open(u);
        }

        void open(User u) {
            dispose();
            new FindIt(u).setVisible(true);
        }

        void err(String m) {
            JOptionPane.showMessageDialog(this, m, "FINDIT", JOptionPane.WARNING_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
    }
}
