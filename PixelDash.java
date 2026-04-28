import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.Preferences;
import javax.swing.*;

/**
 * PixelDash — a pixel-art endless runner built with pure Java Swing/AWT.
 * No external libraries required. Single-file project.
 *
 * How to run:
 *   javac PixelDash.java
 *   java PixelDash
 *
 * Controls:
 *   SPACE / UP / W  — Jump (double jump supported)
 *   DOWN / S        — Slide (duck under flying obstacles)
 *   ENTER           — Start / Restart
 *   ESC             — Back to menu / Quit
 */
public class PixelDash extends JPanel implements ActionListener, KeyListener {

    // ---------- Window & timing ----------
    private static final int W = 960;
    private static final int H = 540;
    private static final int GROUND_Y = 430;
    private static final int FPS = 60;

    // ---------- Game states ----------
    private enum State { MENU, CUSTOMIZE, PLAYING, GAME_OVER }
    private State state = State.MENU;

    // ---------- Player ----------
    private double playerY = GROUND_Y;
    private double velY = 0;
    private final int playerX = 140;
    private final int playerW = 38;
    private int playerH = 56;
    private int jumpsUsed = 0;
    private boolean sliding = false;
    private int slideTicks = 0;
    private int animTick = 0;

    // ---------- World ----------
    private double worldSpeed = 7.0;
    private double distance = 0;
    private int score = 0;
    private int coins = 0;
    private int highScore = 0;
    private final Random rng = new Random();
    private final ArrayList<Obstacle> obstacles = new ArrayList<>();
    private final ArrayList<Coin> coinList = new ArrayList<>();
    private final ArrayList<Cloud> clouds = new ArrayList<>();
    private final ArrayList<Mountain> mountains = new ArrayList<>();
    private int spawnCooldown = 0;
    private int coinCooldown = 0;

    // ---------- Customization ----------
    private final Color[] skinColors = {
        new Color(0xF4C28A), new Color(0xD9A066), new Color(0x8B5A2B),
        new Color(0x4F2E1B), new Color(0x9CD66B)
    };
    private final Color[] outfitColors = {
        new Color(0xE63946), new Color(0x2A9D8F), new Color(0x457B9D),
        new Color(0xF4A261), new Color(0x9D4EDD), new Color(0xFFD166)
    };
    private final String[] hatNames = { "None", "Cap", "Crown", "Helmet", "Mohawk" };
    private int skinIdx = 0;
    private int outfitIdx = 0;
    private int hatIdx = 1;
    private int customizeRow = 0; // 0=skin, 1=outfit, 2=hat

    // ---------- Menu ----------
    private int menuIdx = 0;
    private final String[] menuItems = { "Start Run", "Customize", "Quit" };

    // ---------- Persistence ----------
    private final Preferences prefs = Preferences.userNodeForPackage(PixelDash.class);

    // ---------- Timer ----------
    private final Timer timer;

    public PixelDash() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(0x6FC3DF));
        setFocusable(true);
        addKeyListener(this);
        loadPrefs();
        spawnInitialBackdrop();
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }

    private void loadPrefs() {
        skinIdx   = clamp(prefs.getInt("skin", 0), 0, skinColors.length - 1);
        outfitIdx = clamp(prefs.getInt("outfit", 0), 0, outfitColors.length - 1);
        hatIdx    = clamp(prefs.getInt("hat", 1), 0, hatNames.length - 1);
        highScore = prefs.getInt("hi", 0);
    }
    private void savePrefs() {
        prefs.putInt("skin", skinIdx);
        prefs.putInt("outfit", outfitIdx);
        prefs.putInt("hat", hatIdx);
        prefs.putInt("hi", highScore);
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------------------------------------------------------------- gameplay
    private void resetRun() {
        playerY = GROUND_Y;
        velY = 0;
        jumpsUsed = 0;
        sliding = false;
        slideTicks = 0;
        playerH = 56;
        worldSpeed = 7.0;
        distance = 0;
        score = 0;
        coins = 0;
        obstacles.clear();
        coinList.clear();
        spawnCooldown = 60;
        coinCooldown = 30;
    }

    private void spawnInitialBackdrop() {
        for (int i = 0; i < 5; i++) {
            clouds.add(new Cloud(rng.nextInt(W), 40 + rng.nextInt(120), 0.8 + rng.nextDouble()));
        }
        for (int i = 0; i < 4; i++) {
            mountains.add(new Mountain(i * 280 + rng.nextInt(80), 220 + rng.nextInt(60)));
        }
    }

    private void jump() {
        if (jumpsUsed < 2) {
            velY = (jumpsUsed == 0) ? -14.5 : -12.0;
            jumpsUsed++;
            sliding = false;
            playerH = 56;
        }
    }

    private void startSlide() {
        if (playerY >= GROUND_Y - 0.5 && !sliding) {
            sliding = true;
            slideTicks = 28;
            playerH = 30;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animTick++;
        // backdrop always moves slowly
        for (Cloud c : clouds) {
            c.x -= c.speed * (state == State.PLAYING ? 1.6 : 0.6);
            if (c.x < -80) { c.x = W + rng.nextInt(120); c.y = 40 + rng.nextInt(120); }
        }
        for (Mountain m : mountains) {
            m.x -= state == State.PLAYING ? 1.5 : 0.4;
            if (m.x < -260) { m.x = W + rng.nextInt(160); m.h = 220 + rng.nextInt(60); }
        }

        if (state == State.PLAYING) {
            tickPlaying();
        }
        repaint();
    }

    private void tickPlaying() {
        // physics
        velY += 0.7;
        playerY += velY;
        if (playerY > GROUND_Y) { playerY = GROUND_Y; velY = 0; jumpsUsed = 0; }

        if (sliding) {
            slideTicks--;
            if (slideTicks <= 0) { sliding = false; playerH = 56; }
        }

        // world scrolls
        distance += worldSpeed;
        score = (int) (distance / 6) + coins * 10;
        worldSpeed = Math.min(16.0, 7.0 + distance / 4500.0);

        // spawn obstacles
        spawnCooldown--;
        if (spawnCooldown <= 0) {
            int kind = rng.nextInt(10);
            if (kind < 5) {
                // ground spike block, low jump
                int h = 30 + rng.nextInt(20);
                obstacles.add(new Obstacle(W + 20, GROUND_Y - h, 30, h, false));
            } else if (kind < 8) {
                // tall block, requires good jump or double jump
                int h = 60 + rng.nextInt(25);
                obstacles.add(new Obstacle(W + 20, GROUND_Y - h, 36, h, false));
            } else {
                // flying obstacle, must slide
                obstacles.add(new Obstacle(W + 20, GROUND_Y - 60, 50, 22, true));
            }
            spawnCooldown = (int) (40 + rng.nextInt(35) - Math.min(20, distance / 1500));
        }

        // spawn coins
        coinCooldown--;
        if (coinCooldown <= 0) {
            int row = rng.nextInt(3);
            int yBase = GROUND_Y - 30 - row * 45;
            int count = 3 + rng.nextInt(4);
            for (int i = 0; i < count; i++) {
                coinList.add(new Coin(W + 20 + i * 28, yBase));
            }
            coinCooldown = 80 + rng.nextInt(80);
        }

        // move + cull
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            o.x -= worldSpeed;
            if (o.x + o.w < 0) obstacles.remove(i);
        }
        for (int i = coinList.size() - 1; i >= 0; i--) {
            Coin c = coinList.get(i);
            c.x -= worldSpeed;
            if (c.x + 18 < 0) coinList.remove(i);
        }

        // collisions
        Rectangle pBox = playerBox();
        for (Obstacle o : obstacles) {
            if (pBox.intersects(o.box())) { gameOver(); return; }
        }
        for (int i = coinList.size() - 1; i >= 0; i--) {
            Coin c = coinList.get(i);
            if (pBox.intersects(new Rectangle(c.x, c.y, 18, 18))) {
                coinList.remove(i);
                coins++;
            }
        }
    }

    private Rectangle playerBox() {
        return new Rectangle(playerX + 4, (int)(playerY - playerH), playerW - 8, playerH);
    }

    private void gameOver() {
        state = State.GAME_OVER;
        if (score > highScore) { highScore = score; savePrefs(); }
    }

    // ---------------------------------------------------------------- input
    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        switch (state) {
            case MENU:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) menuIdx = (menuIdx + menuItems.length - 1) % menuItems.length;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) menuIdx = (menuIdx + 1) % menuItems.length;
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                    if (menuIdx == 0) { resetRun(); state = State.PLAYING; }
                    else if (menuIdx == 1) { state = State.CUSTOMIZE; }
                    else { System.exit(0); }
                } else if (k == KeyEvent.VK_ESCAPE) System.exit(0);
                break;
            case CUSTOMIZE:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) customizeRow = (customizeRow + 2) % 3;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) customizeRow = (customizeRow + 1) % 3;
                else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) cycle(-1);
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) cycle(1);
                else if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_ENTER) { savePrefs(); state = State.MENU; }
                break;
            case PLAYING:
                if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP || k == KeyEvent.VK_W) jump();
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) startSlide();
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
            case GAME_OVER:
                if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) { resetRun(); state = State.PLAYING; }
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
        }
    }
    private void cycle(int dir) {
        if (customizeRow == 0) skinIdx = (skinIdx + dir + skinColors.length) % skinColors.length;
        else if (customizeRow == 1) outfitIdx = (outfitIdx + dir + outfitColors.length) % outfitColors.length;
        else hatIdx = (hatIdx + dir + hatNames.length) % hatNames.length;
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // ---------------------------------------------------------------- draw
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // sky gradient
        GradientPaint sky = new GradientPaint(0, 0, new Color(0x6FC3DF), 0, H, new Color(0xC8EEFF));
        g.setPaint(sky);
        g.fillRect(0, 0, W, H);

        drawBackdrop(g);
        drawGround(g);

        switch (state) {
            case MENU:      drawPlayer(g, true); drawMenu(g); break;
            case CUSTOMIZE: drawCustomize(g); break;
            case PLAYING:   drawWorld(g); drawPlayer(g, false); drawHUD(g); break;
            case GAME_OVER: drawWorld(g); drawPlayer(g, false); drawHUD(g); drawGameOver(g); break;
        }
    }

    private void drawBackdrop(Graphics2D g) {
        // mountains
        for (Mountain m : mountains) {
            int x = (int) m.x;
            g.setColor(new Color(0x4A6FA5));
            int[] xs = { x, x + 130, x + 260 };
            int[] ys = { GROUND_Y, GROUND_Y - m.h, GROUND_Y };
            g.fillPolygon(xs, ys, 3);
            g.setColor(new Color(0xE8F1FF));
            int[] sxs = { x + 110, x + 130, x + 150 };
            int[] sys = { GROUND_Y - m.h + 28, GROUND_Y - m.h, GROUND_Y - m.h + 28 };
            g.fillPolygon(sxs, sys, 3);
        }
        // clouds
        for (Cloud c : clouds) {
            g.setColor(Color.WHITE);
            int x = (int) c.x, y = (int) c.y;
            g.fillOval(x, y, 50, 28);
            g.fillOval(x + 25, y - 10, 45, 30);
            g.fillOval(x + 50, y, 45, 26);
        }
    }

    private void drawGround(Graphics2D g) {
        // grass top
        g.setColor(new Color(0x6BBF59));
        g.fillRect(0, GROUND_Y, W, 14);
        // dirt
        g.setColor(new Color(0x8B5A2B));
        g.fillRect(0, GROUND_Y + 14, W, H - GROUND_Y - 14);
        // dirt pixels
        g.setColor(new Color(0x6E4421));
        int offset = (int) (distance % 32);
        for (int x = -offset; x < W; x += 32) {
            g.fillRect(x, GROUND_Y + 22, 6, 6);
            g.fillRect(x + 16, GROUND_Y + 40, 6, 6);
            g.fillRect(x + 8, GROUND_Y + 70, 6, 6);
        }
    }

    private void drawWorld(Graphics2D g) {
        // obstacles
        for (Obstacle o : obstacles) {
            if (o.flying) {
                // bird-like flying enemy
                g.setColor(new Color(0x6A1B9A));
                g.fillRect(o.x, o.y, o.w, o.h);
                g.setColor(new Color(0xFFD166));
                g.fillRect(o.x + o.w - 8, o.y + 6, 4, 4); // eye
                // wings flap
                int flap = (animTick / 6) % 2 == 0 ? -6 : 4;
                g.setColor(new Color(0x4A148C));
                g.fillRect(o.x + 8, o.y + flap, 18, 6);
            } else {
                // pixel cactus / spike block
                g.setColor(new Color(0x3E7B27));
                g.fillRect(o.x, o.y, o.w, o.h);
                g.setColor(new Color(0x2D5A1B));
                g.fillRect(o.x + 4, o.y + 4, 4, o.h - 8);
                g.fillRect(o.x + o.w - 8, o.y + 8, 4, o.h - 12);
                // top spikes
                g.setColor(new Color(0x2D5A1B));
                for (int i = 0; i < o.w; i += 8) {
                    int[] tx = { o.x + i, o.x + i + 4, o.x + i + 8 };
                    int[] ty = { o.y, o.y - 6, o.y };
                    g.fillPolygon(tx, ty, 3);
                }
            }
        }
        // coins
        for (Coin c : coinList) {
            int pulse = (animTick / 6) % 4;
            int squish = pulse == 0 ? 0 : (pulse == 2 ? 4 : 2);
            g.setColor(new Color(0xFFD700));
            g.fillOval(c.x + squish/2, c.y, 18 - squish, 18);
            g.setColor(new Color(0xB8860B));
            g.fillRect(c.x + 7, c.y + 4, 4, 10);
        }
    }

    private void drawPlayer(Graphics2D g, boolean idle) {
        int x = playerX;
        int y = (int) playerY;
        int h = playerH;
        int bob = idle ? (int) (Math.sin(animTick * 0.1) * 2) : 0;
        y += bob;

        Color skin = skinColors[skinIdx];
        Color outfit = outfitColors[outfitIdx];
        Color outfitDark = outfit.darker();

        // shadow
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 2, GROUND_Y + 4, playerW + 4, 8);

        if (sliding) {
            // sliding pose: low rectangle
            g.setColor(outfit);
            g.fillRect(x, y - h, playerW, h);
            g.setColor(skin);
            g.fillRect(x + playerW - 16, y - h, 16, 14); // head out front
            drawHat(g, x + playerW - 16, y - h, 16);
            return;
        }

        // legs (animated when running)
        int legPhase = (animTick / 5) % 2;
        g.setColor(outfitDark);
        if (idle) {
            g.fillRect(x + 6, y - 12, 10, 12);
            g.fillRect(x + playerW - 16, y - 12, 10, 12);
        } else {
            if (legPhase == 0) {
                g.fillRect(x + 4, y - 14, 10, 14);
                g.fillRect(x + playerW - 14, y - 8, 10, 8);
            } else {
                g.fillRect(x + 4, y - 8, 10, 8);
                g.fillRect(x + playerW - 14, y - 14, 10, 14);
            }
        }

        // body / overalls
        g.setColor(outfit);
        g.fillRect(x + 4, y - h + 18, playerW - 8, h - 30);
        // belt
        g.setColor(outfitDark);
        g.fillRect(x + 4, y - h + 30, playerW - 8, 4);
        // arms
        g.setColor(skin);
        int armSwing = idle ? 0 : (legPhase == 0 ? -3 : 3);
        g.fillRect(x - 2, y - h + 22 + armSwing, 8, 18);
        g.fillRect(x + playerW - 6, y - h + 22 - armSwing, 8, 18);

        // head
        g.setColor(skin);
        g.fillRect(x + 4, y - h, playerW - 8, 18);
        // eye
        g.setColor(Color.WHITE);
        g.fillRect(x + 22, y - h + 6, 6, 6);
        g.setColor(Color.BLACK);
        g.fillRect(x + 24, y - h + 8, 3, 3);
        // mouth
        g.setColor(new Color(0x6B3A1A));
        g.fillRect(x + 18, y - h + 14, 8, 2);

        drawHat(g, x + 4, y - h, playerW - 8);
    }

    private void drawHat(Graphics2D g, int hx, int hy, int hw) {
        switch (hatIdx) {
            case 0: return; // None
            case 1: // Cap
                g.setColor(new Color(0xD62828));
                g.fillRect(hx - 2, hy - 6, hw + 4, 6);
                g.fillRect(hx + hw - 6, hy - 4, 14, 4);
                g.setColor(Color.WHITE);
                g.fillRect(hx + hw/2 - 3, hy - 4, 6, 2);
                break;
            case 2: // Crown
                g.setColor(new Color(0xFFD700));
                g.fillRect(hx, hy - 8, hw, 4);
                int[] cx = { hx, hx + 4, hx + 8, hx + hw/2 - 4, hx + hw/2, hx + hw/2 + 4, hx + hw - 8, hx + hw - 4, hx + hw };
                int[] cy = { hy - 8, hy - 14, hy - 8, hy - 8, hy - 14, hy - 8, hy - 8, hy - 14, hy - 8 };
                g.fillPolygon(cx, cy, 9);
                g.setColor(new Color(0xC8102E));
                g.fillRect(hx + hw/2 - 2, hy - 12, 4, 4);
                break;
            case 3: // Helmet
                g.setColor(new Color(0x4A4A4A));
                g.fillRect(hx - 2, hy - 8, hw + 4, 10);
                g.setColor(new Color(0x2A2A2A));
                g.fillRect(hx - 2, hy - 2, hw + 4, 4);
                g.setColor(new Color(0xFFD166));
                g.fillRect(hx + hw/2 - 2, hy - 8, 4, 4);
                break;
            case 4: // Mohawk
                g.setColor(new Color(0xE63946));
                g.fillRect(hx + hw/2 - 2, hy - 10, 4, 10);
                g.fillRect(hx + hw/2 - 4, hy - 8, 8, 6);
                break;
        }
    }

    // ---------------------------------------------------------------- UI
    private void drawHUD(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(16, 14, 240, 64, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(pixelFont(18));
        g.drawString("SCORE  " + score, 32, 38);
        g.drawString("COINS  " + coins, 32, 62);
        g.setFont(pixelFont(14));
        g.drawString("HI " + Math.max(highScore, score), 180, 38);
        g.drawString("SPD " + String.format("%.1f", worldSpeed), 180, 62);
    }

    private void drawMenu(Graphics2D g) {
        // dim
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, W, H);

        g.setFont(pixelFont(64));
        String title = "PIXEL DASH";
        FontMetrics fm = g.getFontMetrics();
        int tx = (W - fm.stringWidth(title)) / 2;
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(title, tx + 4, 124);
        g.setColor(new Color(0xFFD166));
        g.drawString(title, tx, 120);

        g.setFont(pixelFont(20));
        g.setColor(Color.WHITE);
        String sub = "an endless pixel runner";
        int sx = (W - g.getFontMetrics().stringWidth(sub)) / 2;
        g.drawString(sub, sx, 156);

        // menu items
        g.setFont(pixelFont(28));
        for (int i = 0; i < menuItems.length; i++) {
            String s = (menuIdx == i ? "> " : "  ") + menuItems[i] + (menuIdx == i ? " <" : "  ");
            int x = (W - g.getFontMetrics().stringWidth(s)) / 2;
            g.setColor(menuIdx == i ? new Color(0xFFD166) : Color.WHITE);
            g.drawString(s, x, 260 + i * 48);
        }

        g.setFont(pixelFont(14));
        g.setColor(new Color(255, 255, 255, 200));
        String hint = "↑/↓ navigate · ENTER select · SPACE jump · ↓ slide · ESC quit";
        int hx = (W - g.getFontMetrics().stringWidth(hint)) / 2;
        g.drawString(hint, hx, H - 30);

        if (highScore > 0) {
            String hi = "HIGH SCORE  " + highScore;
            g.setFont(pixelFont(16));
            g.setColor(new Color(0xFFD700));
            int hix = (W - g.getFontMetrics().stringWidth(hi)) / 2;
            g.drawString(hi, hix, H - 58);
        }
    }

    private void drawCustomize(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, W, H);

        g.setFont(pixelFont(40));
        g.setColor(new Color(0xFFD166));
        String t = "CUSTOMIZE";
        g.drawString(t, (W - g.getFontMetrics().stringWidth(t)) / 2, 80);

        // preview character on left
        drawPlayer(g, true);

        // panel on right
        int px = 460, py = 160;
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(px, py, 460, 260, 16, 16);

        String[] labels = { "SKIN", "OUTFIT", "HAT" };
        for (int i = 0; i < 3; i++) {
            int rowY = py + 50 + i * 70;
            g.setFont(pixelFont(20));
            g.setColor(customizeRow == i ? new Color(0xFFD166) : Color.WHITE);
            g.drawString((customizeRow == i ? "> " : "  ") + labels[i], px + 24, rowY);

            // arrows
            g.setColor(customizeRow == i ? new Color(0xFFD166) : new Color(255, 255, 255, 180));
            g.drawString("<", px + 200, rowY);
            g.drawString(">", px + 420, rowY);

            // value swatch
            if (i == 0) {
                g.setColor(skinColors[skinIdx]);
                g.fillRoundRect(px + 240, rowY - 24, 160, 32, 8, 8);
            } else if (i == 1) {
                g.setColor(outfitColors[outfitIdx]);
                g.fillRoundRect(px + 240, rowY - 24, 160, 32, 8, 8);
            } else {
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRoundRect(px + 240, rowY - 24, 160, 32, 8, 8);
                g.setFont(pixelFont(18));
                g.setColor(Color.WHITE);
                String v = hatNames[hatIdx];
                g.drawString(v, px + 240 + (160 - g.getFontMetrics().stringWidth(v)) / 2, rowY);
            }
        }

        g.setFont(pixelFont(14));
        g.setColor(new Color(255, 255, 255, 200));
        String hint = "↑/↓ row · ←/→ change · ENTER save & back";
        g.drawString(hint, (W - g.getFontMetrics().stringWidth(hint)) / 2, H - 30);
    }

    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, W, H);

        g.setFont(pixelFont(56));
        g.setColor(new Color(0xE63946));
        String t = "GAME OVER";
        int tx = (W - g.getFontMetrics().stringWidth(t)) / 2;
        g.drawString(t, tx + 3, 183);
        g.setColor(Color.WHITE);
        g.drawString(t, tx, 180);

        g.setFont(pixelFont(24));
        g.setColor(Color.WHITE);
        String s = "SCORE  " + score;
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, 240);
        String c = "COINS  " + coins;
        g.drawString(c, (W - g.getFontMetrics().stringWidth(c)) / 2, 274);

        g.setFont(pixelFont(20));
        g.setColor(new Color(0xFFD700));
        String hi = (score >= highScore && score > 0 ? "NEW HIGH SCORE!  " : "HIGH SCORE  ") + highScore;
        g.drawString(hi, (W - g.getFontMetrics().stringWidth(hi)) / 2, 320);

        g.setFont(pixelFont(18));
        g.setColor(Color.WHITE);
        String h1 = "ENTER  Run again";
        String h2 = "ESC    Main menu";
        g.drawString(h1, (W - g.getFontMetrics().stringWidth(h1)) / 2, 380);
        g.drawString(h2, (W - g.getFontMetrics().stringWidth(h2)) / 2, 408);
    }

    private Font pixelFont(int size) {
        return new Font(Font.MONOSPACED, Font.BOLD, size);
    }

    // ---------------------------------------------------------------- inner data
    static class Obstacle {
        int x, y, w, h; boolean flying;
        Obstacle(int x, int y, int w, int h, boolean flying) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.flying = flying;
        }
        Rectangle box() { return new Rectangle(x, y, w, h); }
    }
    static class Coin { int x, y; Coin(int x, int y) { this.x = x; this.y = y; } }
    static class Cloud { double x, y, speed; Cloud(double x, double y, double s) { this.x = x; this.y = y; this.speed = s; } }
    static class Mountain { double x; int h; Mountain(double x, int h) { this.x = x; this.h = h; } }

    // ---------------------------------------------------------------- main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Pixel Dash — Endless Runner");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            PixelDash game = new PixelDash();
            f.add(game);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
