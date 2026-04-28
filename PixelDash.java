import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;

public class PixelDash extends JPanel implements ActionListener, KeyListener {
    // ===== Constants =====
    static final int W = 900, H = 480;
    static final int GROUND_Y = 380;
    static final int FPS = 60;
    static final String LB_FILE = "pixeldash_leaderboard.txt";
    static final int LB_MAX = 10;
    static final int NAME_MAX = 10;

    enum State { MENU, CUSTOMIZE, NAME_INPUT, PLAYING, PAUSED, GAME_OVER, LEADERBOARD }
    State state = State.MENU;

    // ===== Player =====
    double px = 120, py = GROUND_Y, vy = 0;
    boolean sliding = false;
    int jumpsLeft = 2;
    int animTick = 0;
    boolean shielded = false;
    int shieldTimer = 0;
    int magnetTimer = 0;
    int invincibleTimer = 0; // brief i-frames after hit if shield

    // ===== World =====
    double worldSpeed = 6.0;
    double distance = 0;
    int score = 0;
    int coins = 0;
    int highScore = 0;
    String playerName = "PLAYER";
    StringBuilder nameBuf = new StringBuilder();

    List<Obstacle> obstacles = new ArrayList<>();
    List<Coin> coinList = new ArrayList<>();
    List<Bird> birds = new ArrayList<>();
    List<Cloud> clouds = new ArrayList<>();
    List<PowerUp> powerUps = new ArrayList<>();
    List<Particle> particles = new ArrayList<>();
    int spawnCooldown = 0;
    int birdSpawnCooldown = 0;
    int powerSpawnCooldown = 600;

    // Day/night
    double timeOfDay = 0; // 0..1

    // Customization
    int skinIdx = 0, outfitIdx = 0, hatIdx = 0;
    Color[] skins = { new Color(255,220,177), new Color(241,194,125), new Color(198,134,66),
                      new Color(141,85,36), new Color(90,56,37) };
    Color[] outfits = { new Color(220,50,60), new Color(40,120,220), new Color(50,180,90),
                        new Color(240,180,40), new Color(160,80,200), new Color(40,40,40) };
    String[] hatNames = { "None", "Cap", "Crown", "Helmet", "Mohawk" };

    // Menu
    int menuSel = 0;
    int customSel = 0; // 0 skin, 1 outfit, 2 hat
    int gameOverSel = 0;

    // Leaderboard
    List<Score> leaderboard = new ArrayList<>();

    javax.swing.Timer timer;
    Random rng = new Random();
    Preferences prefs = Preferences.userNodeForPackage(PixelDash.class);

    // Input
    Set<Integer> keys = new HashSet<>();

    public PixelDash() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(135, 206, 235));
        setFocusable(true);
        addKeyListener(this);
        loadPrefs();
        loadLeaderboard();
        for (int i = 0; i < 6; i++) clouds.add(new Cloud(rng.nextInt(W), 40 + rng.nextInt(120), 0.4 + rng.nextDouble() * 0.6));
        timer = new javax.swing.Timer(1000 / FPS, this);
        timer.start();
    }

    void loadPrefs() {
        skinIdx = prefs.getInt("skin", 0);
        outfitIdx = prefs.getInt("outfit", 0);
        hatIdx = prefs.getInt("hat", 0);
        highScore = prefs.getInt("highScore", 0);
        playerName = prefs.get("name", "PLAYER");
    }
    void savePrefs() {
        prefs.putInt("skin", skinIdx);
        prefs.putInt("outfit", outfitIdx);
        prefs.putInt("hat", hatIdx);
        prefs.putInt("highScore", highScore);
        prefs.put("name", playerName);
    }

    void loadLeaderboard() {
        leaderboard.clear();
        File f = new File(LB_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 2);
                if (p.length == 2) {
                    try { leaderboard.add(new Score(p[1], Integer.parseInt(p[0]))); } catch (Exception ignored) {}
                }
            }
        } catch (IOException ignored) {}
        sortLeaderboard();
    }
    void saveLeaderboard() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LB_FILE))) {
            for (Score s : leaderboard) pw.println(s.score + "," + s.name);
        } catch (IOException ignored) {}
    }
    void sortLeaderboard() {
        leaderboard.sort((a, b) -> Integer.compare(b.score, a.score));
        while (leaderboard.size() > LB_MAX) leaderboard.remove(leaderboard.size() - 1);
    }
    void submitScore() {
        leaderboard.add(new Score(playerName, score));
        sortLeaderboard();
        saveLeaderboard();
        if (score > highScore) { highScore = score; savePrefs(); }
    }

    // ===== Game lifecycle =====
    void startGame() {
        obstacles.clear(); coinList.clear(); birds.clear(); powerUps.clear(); particles.clear();
        px = 120; py = GROUND_Y; vy = 0; sliding = false; jumpsLeft = 2;
        worldSpeed = 6.0; distance = 0; score = 0; coins = 0;
        spawnCooldown = 60; birdSpawnCooldown = 200; powerSpawnCooldown = 500;
        shielded = false; shieldTimer = 0; magnetTimer = 0; invincibleTimer = 0;
        state = State.PLAYING;
    }

    void gameOver() {
        for (int i = 0; i < 30; i++) particles.add(new Particle(px, py - 20, rng));
        submitScore();
        state = State.GAME_OVER;
        gameOverSel = 0;
    }

    // ===== Update =====
    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == State.PLAYING) updateGame();
        // animate clouds always
        for (Cloud c : clouds) { c.x -= c.spd * (state == State.PLAYING ? 1 : 0.3); if (c.x < -80) { c.x = W + 40; c.y = 40 + rng.nextInt(120); } }
        timeOfDay += 0.0008;
        if (timeOfDay > 1) timeOfDay -= 1;
        repaint();
    }

    void updateGame() {
        animTick++;
        distance += worldSpeed;
        score = (int) (distance / 5) + coins * 10;
        worldSpeed = Math.min(14.0, 6.0 + distance / 4000.0);

        // Physics
        vy += 0.9;
        py += vy;
        if (py >= GROUND_Y) { py = GROUND_Y; vy = 0; jumpsLeft = 2; }

        // Slide hold
        sliding = keys.contains(KeyEvent.VK_DOWN) || keys.contains(KeyEvent.VK_S);

        // Spawn obstacles
        if (--spawnCooldown <= 0) {
            int t = rng.nextInt(3);
            if (t == 0) obstacles.add(new Obstacle(W + 20, GROUND_Y - 20, 22, 22, 0)); // spike
            else if (t == 1) obstacles.add(new Obstacle(W + 20, GROUND_Y - 50, 30, 50, 1)); // block
            else obstacles.add(new Obstacle(W + 20, GROUND_Y - 90, 36, 26, 2)); // flying enemy (low fly)
            spawnCooldown = 50 + rng.nextInt(60) - (int)(worldSpeed - 6) * 3;
            if (spawnCooldown < 25) spawnCooldown = 25;
            // Often add coins above
            if (rng.nextDouble() < 0.7) {
                int cy = GROUND_Y - 60 - rng.nextInt(80);
                int n = 3 + rng.nextInt(4);
                for (int i = 0; i < n; i++) coinList.add(new Coin(W + 40 + i * 24, cy));
            }
        }
        // Spawn birds (decorative + occasional hazard low-flying)
        if (--birdSpawnCooldown <= 0) {
            boolean hazard = rng.nextDouble() < 0.4;
            int by = hazard ? GROUND_Y - 80 - rng.nextInt(30) : 60 + rng.nextInt(140);
            birds.add(new Bird(W + 20, by, hazard));
            birdSpawnCooldown = 120 + rng.nextInt(180);
        }
        // Power-ups
        if (--powerSpawnCooldown <= 0) {
            int type = rng.nextInt(3); // 0 shield, 1 magnet, 2 coin bag
            powerUps.add(new PowerUp(W + 20, GROUND_Y - 90 - rng.nextInt(60), type));
            powerSpawnCooldown = 600 + rng.nextInt(400);
        }

        // Move entities
        for (Obstacle o : obstacles) o.x -= worldSpeed;
        for (Coin c : coinList) c.x -= worldSpeed;
        for (Bird b : birds) b.update(worldSpeed);
        for (PowerUp p : powerUps) p.x -= worldSpeed;
        for (Particle pt : particles) pt.update();

        obstacles.removeIf(o -> o.x + o.w < -10);
        coinList.removeIf(c -> c.collected || c.x < -20);
        birds.removeIf(b -> b.x < -40);
        powerUps.removeIf(p -> p.collected || p.x < -20);
        particles.removeIf(p -> p.life <= 0);

        // Magnet pulls coins
        if (magnetTimer > 0) {
            magnetTimer--;
            for (Coin c : coinList) {
                double dx = px - c.x, dy = (py - 30) - c.y;
                double d = Math.hypot(dx, dy);
                if (d < 160) { c.x += dx / d * 5; c.y += dy / d * 5; }
            }
        }
        if (shieldTimer > 0) { shieldTimer--; if (shieldTimer == 0) shielded = false; }
        if (invincibleTimer > 0) invincibleTimer--;

        // Collisions
        Rectangle pr = playerRect();
        for (Obstacle o : obstacles) {
            if (pr.intersects(o.rect())) {
                if (shielded || invincibleTimer > 0) {
                    if (shielded) {
                        shielded = false; shieldTimer = 0; invincibleTimer = 60;
                        for (int i = 0; i < 20; i++) particles.add(new Particle(px, py - 20, rng));
                        o.x = -100; // remove
                    }
                } else { gameOver(); return; }
            }
        }
        for (Bird b : birds) {
            if (b.hazard && pr.intersects(b.rect())) {
                if (shielded || invincibleTimer > 0) {
                    if (shielded) { shielded = false; shieldTimer = 0; invincibleTimer = 60; b.x = -100; }
                } else { gameOver(); return; }
            }
        }
        for (Coin c : coinList) {
            if (!c.collected && pr.intersects(c.rect())) { c.collected = true; coins++; }
        }
        for (PowerUp p : powerUps) {
            if (!p.collected && pr.intersects(p.rect())) {
                p.collected = true;
                if (p.type == 0) { shielded = true; shieldTimer = 360; }
                else if (p.type == 1) { magnetTimer = 480; }
                else { coins += 10; }
            }
        }
    }

    Rectangle playerRect() {
        if (sliding) return new Rectangle((int)px - 14, (int)py - 22, 36, 22);
        return new Rectangle((int)px - 14, (int)py - 46, 28, 46);
    }

    // ===== Rendering =====
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawSky(g2);
        drawMountains(g2);
        for (Cloud c : clouds) drawCloud(g2, (int)c.x, (int)c.y);
        for (Bird b : birds) drawBird(g2, b);
        drawGround(g2);

        if (state == State.PLAYING || state == State.PAUSED || state == State.GAME_OVER) {
            for (Coin c : coinList) if (!c.collected) drawCoin(g2, (int)c.x, (int)c.y);
            for (PowerUp p : powerUps) if (!p.collected) drawPowerUp(g2, p);
            for (Obstacle o : obstacles) drawObstacle(g2, o);
            drawPlayer(g2);
            for (Particle pt : particles) pt.draw(g2);
            drawHUD(g2);
        }

        switch (state) {
            case MENU: drawMenu(g2); break;
            case CUSTOMIZE: drawCustomize(g2); break;
            case NAME_INPUT: drawNameInput(g2); break;
            case PAUSED: drawPaused(g2); break;
            case GAME_OVER: drawGameOver(g2); break;
            case LEADERBOARD: drawLeaderboard(g2); break;
            default: break;
        }
    }

    void drawSky(Graphics2D g2) {
        // Day/night cycle
        double t = timeOfDay;
        Color top, bot;
        if (t < 0.5) { // day -> dusk
            float k = (float)(t / 0.5);
            top = lerp(new Color(135,206,235), new Color(255,140,90), k);
            bot = lerp(new Color(200,230,255), new Color(255,200,140), k);
        } else { // night -> dawn
            float k = (float)((t - 0.5) / 0.5);
            top = lerp(new Color(20,20,60), new Color(135,206,235), k);
            bot = lerp(new Color(60,40,80), new Color(200,230,255), k);
        }
        GradientPaint gp = new GradientPaint(0, 0, top, 0, GROUND_Y, bot);
        g2.setPaint(gp);
        g2.fillRect(0, 0, W, GROUND_Y);
        // stars at night
        if (t > 0.55 && t < 0.95) {
            g2.setColor(Color.WHITE);
            Random sr = new Random(42);
            for (int i = 0; i < 40; i++) g2.fillRect(sr.nextInt(W), sr.nextInt(GROUND_Y - 100), 2, 2);
        }
    }
    Color lerp(Color a, Color b, float k) {
        k = Math.max(0, Math.min(1, k));
        return new Color(
            (int)(a.getRed()+(b.getRed()-a.getRed())*k),
            (int)(a.getGreen()+(b.getGreen()-a.getGreen())*k),
            (int)(a.getBlue()+(b.getBlue()-a.getBlue())*k));
    }

    void drawMountains(Graphics2D g2) {
        g2.setColor(new Color(80, 110, 80));
        int[] mx = {0, 120, 240, 360, 480, 600, 720, 840, 900};
        int[] my = {GROUND_Y, 240, 280, 200, 260, 220, 290, 230, GROUND_Y};
        g2.fillPolygon(mx, my, mx.length);
        g2.setColor(new Color(60, 90, 60));
        int[] mx2 = {0, 80, 200, 320, 440, 560, 680, 800, 900};
        int[] my2 = {GROUND_Y, 320, 290, 330, 300, 340, 310, 320, GROUND_Y};
        g2.fillPolygon(mx2, my2, mx2.length);
    }

    void drawCloud(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillOval(x, y, 50, 24);
        g2.fillOval(x + 20, y - 10, 40, 28);
        g2.fillOval(x + 40, y, 50, 24);
    }

    void drawBird(Graphics2D g2, Bird b) {
        int phase = (animTick / 6 + (int)b.x) % 4;
        Color body = b.hazard ? new Color(40, 30, 40) : new Color(60, 60, 80);
        g2.setColor(body);
        g2.fillRect((int)b.x, (int)b.y, 14, 6); // body
        g2.fillRect((int)b.x + 3, (int)b.y - 2, 8, 3); // head
        // wings flap
        int wingY = (phase < 2) ? -6 : 4;
        g2.fillRect((int)b.x - 2, (int)b.y + wingY, 8, 3);
        g2.fillRect((int)b.x + 8, (int)b.y + wingY, 8, 3);
        // beak
        g2.setColor(new Color(255, 180, 40));
        g2.fillRect((int)b.x + 11, (int)b.y - 1, 3, 2);
    }

    void drawGround(Graphics2D g2) {
        g2.setColor(new Color(120, 80, 40));
        g2.fillRect(0, GROUND_Y, W, H - GROUND_Y);
        g2.setColor(new Color(90, 200, 90));
        g2.fillRect(0, GROUND_Y, W, 8);
        // scrolling stripes
        g2.setColor(new Color(100, 60, 30));
        int off = (int)(distance) % 40;
        for (int x = -off; x < W; x += 40) g2.fillRect(x, GROUND_Y + 20, 20, 6);
    }

    void drawObstacle(Graphics2D g2, Obstacle o) {
        if (o.type == 0) {
            g2.setColor(new Color(180, 180, 190));
            int[] xs = {(int)o.x, (int)o.x + 11, (int)o.x + 22};
            int[] ys = {(int)o.y + 22, (int)o.y, (int)o.y + 22};
            g2.fillPolygon(xs, ys, 3);
        } else if (o.type == 1) {
            g2.setColor(new Color(150, 90, 40));
            g2.fillRect((int)o.x, (int)o.y, o.w, o.h);
            g2.setColor(new Color(110, 60, 20));
            g2.drawRect((int)o.x, (int)o.y, o.w - 1, o.h - 1);
        } else {
            // flying enemy: pixel critter
            int x = (int)o.x, y = (int)o.y;
            g2.setColor(new Color(180, 50, 60));
            g2.fillRect(x, y, o.w, o.h);
            g2.setColor(Color.WHITE);
            g2.fillRect(x + 6, y + 6, 6, 6);
            g2.fillRect(x + 22, y + 6, 6, 6);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + 8, y + 8, 3, 3);
            g2.fillRect(x + 24, y + 8, 3, 3);
            // wings flapping
            int wf = (animTick / 5) % 2 == 0 ? -4 : 0;
            g2.setColor(new Color(120, 30, 40));
            g2.fillRect(x - 6, y + wf, 8, 4);
            g2.fillRect(x + o.w - 2, y + wf, 8, 4);
        }
    }

    void drawCoin(Graphics2D g2, int x, int y) {
        int s = (animTick / 5) % 4;
        int w = (s == 0 || s == 2) ? 12 : (s == 1 ? 8 : 4);
        g2.setColor(new Color(255, 215, 0));
        g2.fillOval(x - w/2, y - 8, w, 16);
        g2.setColor(new Color(200, 160, 0));
        g2.drawOval(x - w/2, y - 8, w, 16);
    }

    void drawPowerUp(Graphics2D g2, PowerUp p) {
        int x = (int)p.x, y = (int)p.y;
        int bob = (int)(Math.sin(animTick * 0.1 + p.x) * 3);
        if (p.type == 0) { // shield
            g2.setColor(new Color(80, 180, 255));
            g2.fillOval(x - 10, y - 10 + bob, 20, 22);
            g2.setColor(Color.WHITE);
            g2.drawOval(x - 10, y - 10 + bob, 20, 22);
            g2.drawString("S", x - 4, y + 4 + bob);
        } else if (p.type == 1) { // magnet
            g2.setColor(new Color(220, 60, 60));
            g2.fillRect(x - 10, y - 10 + bob, 8, 14);
            g2.fillRect(x + 2, y - 10 + bob, 8, 14);
            g2.setColor(Color.LIGHT_GRAY);
            g2.fillRect(x - 10, y + 4 + bob, 8, 6);
            g2.fillRect(x + 2, y + 4 + bob, 8, 6);
        } else { // coin bag
            g2.setColor(new Color(200, 140, 60));
            g2.fillRect(x - 10, y - 8 + bob, 20, 18);
            g2.setColor(new Color(255, 215, 0));
            g2.drawString("$", x - 3, y + 4 + bob);
        }
    }

    void drawPlayer(Graphics2D g2) {
        int x = (int)px, y = (int)py;
        Color skin = skins[skinIdx];
        Color outfit = outfits[outfitIdx];
        boolean blink = invincibleTimer > 0 && (invincibleTimer / 4) % 2 == 0;
        if (blink) return;
        if (sliding) {
            // body horizontal
            g2.setColor(outfit);
            g2.fillRect(x - 14, y - 14, 30, 14);
            g2.setColor(skin);
            g2.fillRect(x + 14, y - 16, 12, 12); // head
            g2.setColor(Color.BLACK);
            g2.fillRect(x + 22, y - 12, 2, 2);
        } else {
            // legs
            int legPhase = (animTick / 4) % 2;
            g2.setColor(new Color(60,40,20));
            if (legPhase == 0) {
                g2.fillRect(x - 10, y - 10, 8, 10);
                g2.fillRect(x + 2, y - 6, 8, 6);
            } else {
                g2.fillRect(x - 10, y - 6, 8, 6);
                g2.fillRect(x + 2, y - 10, 8, 10);
            }
            // body
            g2.setColor(outfit);
            g2.fillRect(x - 12, y - 32, 24, 22);
            // arms
            g2.setColor(skin);
            g2.fillRect(x - 16, y - 28, 6, 14);
            g2.fillRect(x + 10, y - 28, 6, 14);
            // head
            g2.setColor(skin);
            g2.fillRect(x - 10, y - 46, 20, 16);
            // eye
            g2.setColor(Color.BLACK);
            g2.fillRect(x + 2, y - 40, 2, 2);
            // hat
            drawHat(g2, x, y - 46);
        }
        // shield aura
        if (shielded) {
            g2.setColor(new Color(80, 180, 255, 120));
            g2.drawOval(x - 24, y - 56, 48, 64);
            g2.drawOval(x - 26, y - 58, 52, 68);
        }
        if (magnetTimer > 0) {
            g2.setColor(new Color(220, 60, 60, 100));
            g2.drawOval(x - 30, y - 50, 60, 60);
        }
    }

    void drawHat(Graphics2D g2, int x, int y) {
        switch (hatIdx) {
            case 1: // cap
                g2.setColor(new Color(220, 50, 50));
                g2.fillRect(x - 12, y - 6, 24, 6);
                g2.fillRect(x + 8, y - 2, 10, 4);
                break;
            case 2: // crown
                g2.setColor(new Color(255, 215, 0));
                g2.fillRect(x - 12, y - 4, 24, 4);
                g2.fillRect(x - 10, y - 10, 4, 6);
                g2.fillRect(x - 2, y - 12, 4, 8);
                g2.fillRect(x + 6, y - 10, 4, 6);
                break;
            case 3: // helmet
                g2.setColor(new Color(120, 120, 130));
                g2.fillRect(x - 12, y - 8, 24, 10);
                g2.setColor(new Color(80, 80, 90));
                g2.fillRect(x - 12, y - 2, 24, 2);
                break;
            case 4: // mohawk
                g2.setColor(new Color(220, 60, 200));
                g2.fillRect(x - 2, y - 10, 4, 10);
                g2.fillRect(x - 6, y - 6, 4, 6);
                g2.fillRect(x + 2, y - 6, 4, 6);
                break;
        }
    }

    // ===== HUD & screens =====
    void drawHUD(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 130));
        g2.fillRect(10, 10, 280, 60);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.drawString(playerName + "  Score: " + score, 20, 30);
        g2.drawString("Coins: " + coins + "   High: " + highScore, 20, 52);

        int by = 14;
        if (shieldTimer > 0) { g2.setColor(new Color(80,180,255)); g2.fillRect(W - 130, by, (shieldTimer * 100) / 360, 8); g2.setColor(Color.WHITE); g2.drawString("Shield", W - 60, by + 8); by += 14; }
        if (magnetTimer > 0) { g2.setColor(new Color(220,60,60)); g2.fillRect(W - 130, by, (magnetTimer * 100) / 480, 8); g2.setColor(Color.WHITE); g2.drawString("Magnet", W - 60, by + 8); }

        g2.setColor(new Color(255,255,255,160));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("SPACE/UP jump  DOWN slide  P pause  ESC menu", 20, H - 10);
    }

    void drawCenteredTitle(Graphics2D g2, String s, int y, int size, Color c) {
        g2.setFont(new Font("Monospaced", Font.BOLD, size));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(s);
        g2.setColor(Color.BLACK);
        g2.drawString(s, (W - w) / 2 + 2, y + 2);
        g2.setColor(c);
        g2.drawString(s, (W - w) / 2, y);
    }
    void drawCenteredText(Graphics2D g2, String s, int y, int size, Color c) {
        g2.setFont(new Font("Monospaced", Font.PLAIN, size));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(c);
        g2.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }

    void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(0, 0, W, H);
        drawCenteredTitle(g2, "PIXEL DASH", 110, 56, new Color(255, 215, 0));
        drawCenteredText(g2, "Player: " + playerName + "   High: " + highScore, 150, 16, Color.WHITE);
        String[] items = { "Play", "Customize", "Change Name", "Leaderboard", "Quit" };
        for (int i = 0; i < items.length; i++) {
            Color c = (i == menuSel) ? new Color(255, 215, 0) : Color.WHITE;
            drawCenteredText(g2, (i == menuSel ? "> " : "  ") + items[i] + (i == menuSel ? " <" : ""), 220 + i * 36, 22, c);
        }
        drawCenteredText(g2, "ENTER select  UP/DOWN navigate", H - 24, 12, Color.LIGHT_GRAY);
    }

    void drawCustomize(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, W, H);
        drawCenteredTitle(g2, "CUSTOMIZE", 70, 38, new Color(120, 220, 255));
        // preview
        int oldX = (int)px, oldY = (int)py;
        px = W / 2; py = 280;
        drawPlayer(g2);
        px = oldX; py = oldY;

        String[] labels = { "Skin: " + (skinIdx + 1) + "/" + skins.length,
                            "Outfit: " + (outfitIdx + 1) + "/" + outfits.length,
                            "Hat: " + hatNames[hatIdx] };
        for (int i = 0; i < labels.length; i++) {
            Color c = (i == customSel) ? new Color(255, 215, 0) : Color.WHITE;
            drawCenteredText(g2, (i == customSel ? "> " : "  ") + labels[i], 320 + i * 30, 20, c);
        }
        drawCenteredText(g2, "UP/DOWN switch field   LEFT/RIGHT change   ENTER back", H - 24, 12, Color.LIGHT_GRAY);
    }

    void drawNameInput(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, W, H);
        drawCenteredTitle(g2, "ENTER NAME", 130, 38, new Color(120, 255, 160));
        drawCenteredText(g2, "Max " + NAME_MAX + " characters: A-Z 0-9", 175, 14, Color.LIGHT_GRAY);
        String shown = nameBuf.toString();
        if ((animTick / 20) % 2 == 0) shown += "_";
        // box
        int bx = W / 2 - 140, by = 220, bw = 280, bh = 50;
        g2.setColor(new Color(40, 40, 60));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(Color.WHITE);
        g2.drawRect(bx, by, bw, bh);
        g2.setFont(new Font("Monospaced", Font.BOLD, 26));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(shown, (W - fm.stringWidth(shown)) / 2, by + 35);
        drawCenteredText(g2, "ENTER confirm    BACKSPACE delete    ESC cancel", H - 30, 13, Color.LIGHT_GRAY);
    }

    void drawPaused(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, W, H);
        drawCenteredTitle(g2, "PAUSED", 200, 48, Color.WHITE);
        drawCenteredText(g2, "P resume    ESC main menu", 260, 16, Color.LIGHT_GRAY);
    }

    void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, W, H);
        drawCenteredTitle(g2, "GAME OVER", 110, 48, new Color(255, 100, 100));
        drawCenteredText(g2, playerName + "  -  Score: " + score + "   High: " + highScore, 160, 18, Color.WHITE);
        // mini leaderboard preview
        drawCenteredText(g2, "-- TOP 5 --", 200, 14, new Color(255, 215, 0));
        int n = Math.min(5, leaderboard.size());
        for (int i = 0; i < n; i++) {
            Score s = leaderboard.get(i);
            String line = String.format("%d. %-10s %6d", i + 1, s.name, s.score);
            Color c = s.name.equals(playerName) && s.score == score ? new Color(120, 255, 160) : Color.WHITE;
            drawCenteredText(g2, line, 224 + i * 20, 14, c);
        }
        String[] items = { "Play Again", "Leaderboard", "Main Menu" };
        for (int i = 0; i < items.length; i++) {
            Color c = (i == gameOverSel) ? new Color(255, 215, 0) : Color.WHITE;
            drawCenteredText(g2, (i == gameOverSel ? "> " : "  ") + items[i], 360 + i * 28, 18, c);
        }
    }

    void drawLeaderboard(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, W, H);
        drawCenteredTitle(g2, "LEADERBOARD", 70, 40, new Color(255, 215, 0));
        if (leaderboard.isEmpty()) {
            drawCenteredText(g2, "No scores yet. Go play!", 200, 18, Color.WHITE);
        } else {
            for (int i = 0; i < leaderboard.size(); i++) {
                Score s = leaderboard.get(i);
                String line = String.format("%2d.  %-10s   %6d", i + 1, s.name, s.score);
                Color c = (i == 0) ? new Color(255, 215, 0) : (i == 1 ? new Color(200,200,210) : (i == 2 ? new Color(205,127,50) : Color.WHITE));
                drawCenteredText(g2, line, 130 + i * 26, 18, c);
            }
        }
        drawCenteredText(g2, "C clear leaderboard    ENTER/ESC back", H - 24, 13, Color.LIGHT_GRAY);
    }

    // ===== Input =====
    @Override public void keyPressed(KeyEvent e) {
        keys.add(e.getKeyCode());
        int k = e.getKeyCode();
        switch (state) {
            case MENU:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) menuSel = (menuSel + 4) % 5;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) menuSel = (menuSel + 1) % 5;
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                    if (menuSel == 0) startGame();
                    else if (menuSel == 1) { state = State.CUSTOMIZE; customSel = 0; }
                    else if (menuSel == 2) { nameBuf.setLength(0); state = State.NAME_INPUT; }
                    else if (menuSel == 3) state = State.LEADERBOARD;
                    else System.exit(0);
                }
                break;
            case CUSTOMIZE:
                if (k == KeyEvent.VK_UP) customSel = (customSel + 2) % 3;
                else if (k == KeyEvent.VK_DOWN) customSel = (customSel + 1) % 3;
                else if (k == KeyEvent.VK_LEFT) {
                    if (customSel == 0) skinIdx = (skinIdx + skins.length - 1) % skins.length;
                    else if (customSel == 1) outfitIdx = (outfitIdx + outfits.length - 1) % outfits.length;
                    else hatIdx = (hatIdx + hatNames.length - 1) % hatNames.length;
                    savePrefs();
                } else if (k == KeyEvent.VK_RIGHT) {
                    if (customSel == 0) skinIdx = (skinIdx + 1) % skins.length;
                    else if (customSel == 1) outfitIdx = (outfitIdx + 1) % outfits.length;
                    else hatIdx = (hatIdx + 1) % hatNames.length;
                    savePrefs();
                } else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
            case NAME_INPUT:
                if (k == KeyEvent.VK_ENTER) {
                    if (nameBuf.length() > 0) { playerName = nameBuf.toString(); savePrefs(); }
                    state = State.MENU;
                } else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                else if (k == KeyEvent.VK_BACK_SPACE) { if (nameBuf.length() > 0) nameBuf.deleteCharAt(nameBuf.length() - 1); }
                else {
                    char c = e.getKeyChar();
                    if (nameBuf.length() < NAME_MAX && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                        nameBuf.append(Character.toUpperCase(c));
                    }
                }
                break;
            case PLAYING:
                if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP || k == KeyEvent.VK_W) {
                    if (jumpsLeft > 0) { vy = -15; jumpsLeft--; }
                } else if (k == KeyEvent.VK_P) state = State.PAUSED;
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
            case PAUSED:
                if (k == KeyEvent.VK_P) state = State.PLAYING;
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
            case GAME_OVER:
                if (k == KeyEvent.VK_UP) gameOverSel = (gameOverSel + 2) % 3;
                else if (k == KeyEvent.VK_DOWN) gameOverSel = (gameOverSel + 1) % 3;
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                    if (gameOverSel == 0) startGame();
                    else if (gameOverSel == 1) state = State.LEADERBOARD;
                    else state = State.MENU;
                }
                break;
            case LEADERBOARD:
                if (k == KeyEvent.VK_C) { leaderboard.clear(); saveLeaderboard(); }
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
        }
    }
    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}

    // ===== Inner classes =====
    static class Obstacle {
        double x; int y, w, h, type;
        Obstacle(double x, int y, int w, int h, int type) { this.x = x; this.y = y; this.w = w; this.h = h; this.type = type; }
        Rectangle rect() { return new Rectangle((int)x, y, w, h); }
    }
    static class Coin {
        double x; int y; boolean collected;
        Coin(double x, int y) { this.x = x; this.y = y; }
        Rectangle rect() { return new Rectangle((int)x - 6, y - 8, 12, 16); }
    }
    static class Bird {
        double x, y, baseY; boolean hazard; double t;
        Bird(double x, double y, boolean hazard) { this.x = x; this.y = y; this.baseY = y; this.hazard = hazard; }
        void update(double speed) {
            x -= hazard ? speed * 1.1 : speed * 0.5;
            t += 0.1;
            y = baseY + Math.sin(t) * 6;
        }
        Rectangle rect() { return new Rectangle((int)x, (int)y - 2, 14, 8); }
    }
    static class Cloud {
        double x, y, spd;
        Cloud(double x, double y, double spd) { this.x = x; this.y = y; this.spd = spd; }
    }
    static class PowerUp {
        double x; int y, type; boolean collected;
        PowerUp(double x, int y, int type) { this.x = x; this.y = y; this.type = type; }
        Rectangle rect() { return new Rectangle((int)x - 12, y - 12, 24, 24); }
    }
    static class Particle {
        double x, y, vx, vy; int life; Color c;
        Particle(double x, double y, Random r) {
            this.x = x; this.y = y;
            this.vx = (r.nextDouble() - 0.5) * 6;
            this.vy = -2 - r.nextDouble() * 4;
            this.life = 30 + r.nextInt(20);
            int[] palette = { 0xFFD700, 0xFF6464, 0x64B4FF, 0xFFFFFF };
            this.c = new Color(palette[r.nextInt(palette.length)]);
        }
        void update() { x += vx; y += vy; vy += 0.3; life--; }
        void draw(Graphics2D g2) { g2.setColor(c); g2.fillRect((int)x, (int)y, 4, 4); }
    }
    static class Score {
        String name; int score;
        Score(String name, int score) { this.name = name; this.score = score; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Pixel Dash");
            PixelDash p = new PixelDash();
            f.add(p);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}
