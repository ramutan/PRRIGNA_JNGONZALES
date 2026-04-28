import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.Preferences;
import javax.swing.*;

/**
 * PixelDash - a pixel-art endless runner built with pure Java Swing/AWT.
 * No external libraries required. Single-file project.
 *
 * How to run:
 *   javac PixelDash.java
 *   java PixelDash
 *
 * Controls:
 *   SPACE / UP / W  - Jump
 *   DOWN / S        - Slide
 *   ENTER           - Start / Restart / Buy
 *   ESC             - Back to menu / Quit
 */
public class PixelDash extends JPanel implements ActionListener, KeyListener {

    private static final int W = 960;
    private static final int H = 540;
    private static final int GROUND_Y = 430;
    private static final int FPS = 240;
    private static final double GRAVITY = 0.92;
    private static final double JUMP_VELOCITY = -16.4;

    private enum State { MENU, CUSTOMIZE, MARKET, PLAYING, GAME_OVER }
    private State state = State.MENU;

    private enum ObstacleType {
        STUMP,
        CRATE,
        SPIKE_ROW,
        BARREL,
        TOTEM,
        LOW_SIGN,
        DRONE_HIGH,
        DRONE_LOW
    }

    private double playerY = GROUND_Y;
    private double velY = 0;
    private final int playerX = 140;
    private final int playerW = 38;
    private int playerH = 56;
    private int jumpsUsed = 0;
    private boolean sliding = false;
    private int slideTicks = 0;
    private int animTick = 0;

    private double worldSpeed = 7.0;
    private double distance = 0;
    private int score = 0;
    private int coins = 0;
    private int totalCoins = 0;
    private int highScore = 0;
    private final Random rng = new Random();
    private final ArrayList<Obstacle> obstacles = new ArrayList<>();
    private final ArrayList<Coin> coinList = new ArrayList<>();
    private final ArrayList<Cloud> clouds = new ArrayList<>();
    private final ArrayList<Mountain> mountains = new ArrayList<>();
    private int spawnCooldown = 0;
    private int coinCooldown = 0;
    private int obstacleCount = 0;
    private ObstacleType lastObstacleType = null;
    private int patternStep = 0;
    private final Color skyTop = new Color(0x5DB6F2);
    private final Color skyBottom = new Color(0xF8EED1);

    private final Color[] skinColors = {
        new Color(0xF4C28A), new Color(0xD9A066), new Color(0x8B5A2B),
        new Color(0x4F2E1B), new Color(0x9CD66B)
    };
    private final Color[] outfitColors = {
        new Color(0xE63946), new Color(0x2A9D8F), new Color(0x457B9D),
        new Color(0xF4A261), new Color(0x9D4EDD), new Color(0xFFD166)
    };

    private final String[] hatNames = { "None", "Cap", "Crown", "Helmet", "Mohawk", "Beanie", "Horns" };
    private final int[] hatPrices = { 0, 0, 80, 120, 160, 210, 260 };

    private final String[] faceNames = { "None", "Glasses", "Mask", "Visor", "Headband", "Monocle", "Bandana" };
    private final int[] facePrices = { 0, 70, 110, 150, 180, 220, 250 };

    private final String[] backNames = { "None", "Cape", "Jetpack", "Wings", "Banner", "Backpack", "Halo" };
    private final int[] backPrices = { 0, 90, 140, 190, 230, 270, 320 };

    private int skinIdx = 0;
    private int outfitIdx = 0;
    private int hatIdx = 1;
    private int faceIdx = 0;
    private int backIdx = 0;

    private int customizeRow = 0;
    private boolean[] ownedHats = new boolean[hatNames.length];
    private boolean[] ownedFaces = new boolean[faceNames.length];
    private boolean[] ownedBacks = new boolean[backNames.length];

    private int menuIdx = 0;
    private final String[] menuItems = { "Start Run", "Customize", "Market", "Quit" };

    private int marketRow = 0;
    private int marketHatIdx = 1;
    private int marketFaceIdx = 0;
    private int marketBackIdx = 0;

    private final Preferences prefs = Preferences.userNodeForPackage(PixelDash.class);
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
        skinIdx = clamp(prefs.getInt("skin", 0), 0, skinColors.length - 1);
        outfitIdx = clamp(prefs.getInt("outfit", 0), 0, outfitColors.length - 1);
        hatIdx = clamp(prefs.getInt("hat", 1), 0, hatNames.length - 1);
        faceIdx = clamp(prefs.getInt("face", 0), 0, faceNames.length - 1);
        backIdx = clamp(prefs.getInt("back", 0), 0, backNames.length - 1);
        highScore = prefs.getInt("hi", 0);
        totalCoins = prefs.getInt("bank", 0);
        loadOwnedItems();
        syncMarketSelection();
    }

    private void savePrefs() {
        prefs.putInt("skin", skinIdx);
        prefs.putInt("outfit", outfitIdx);
        prefs.putInt("hat", hatIdx);
        prefs.putInt("face", faceIdx);
        prefs.putInt("back", backIdx);
        prefs.putInt("hi", highScore);
        prefs.putInt("bank", totalCoins);
        prefs.putInt("ownedHats", toMask(ownedHats));
        prefs.putInt("ownedFaces", toMask(ownedFaces));
        prefs.putInt("ownedBacks", toMask(ownedBacks));
    }

    private void loadOwnedItems() {
        int hatMask = prefs.getInt("ownedHats", 0);
        int faceMask = prefs.getInt("ownedFaces", 0);
        int backMask = prefs.getInt("ownedBacks", 0);

        for (int i = 0; i < ownedHats.length; i++) {
            ownedHats[i] = ((hatMask >> i) & 1) == 1;
        }
        for (int i = 0; i < ownedFaces.length; i++) {
            ownedFaces[i] = ((faceMask >> i) & 1) == 1;
        }
        for (int i = 0; i < ownedBacks.length; i++) {
            ownedBacks[i] = ((backMask >> i) & 1) == 1;
        }

        ownedHats[0] = true;
        ownedHats[1] = true;
        ownedFaces[0] = true;
        ownedBacks[0] = true;

        if (!ownedHats[hatIdx]) hatIdx = 0;
        if (!ownedFaces[faceIdx]) faceIdx = 0;
        if (!ownedBacks[backIdx]) backIdx = 0;
    }

    private int toMask(boolean[] values) {
        int mask = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void syncMarketSelection() {
        marketHatIdx = hatIdx;
        marketFaceIdx = faceIdx;
        marketBackIdx = backIdx;
    }

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
        spawnCooldown = 72;
        coinCooldown = 30;
        obstacleCount = 0;
        lastObstacleType = null;
        patternStep = 0;
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
        if (jumpsUsed == 0 && playerY >= GROUND_Y - 0.5) {
            velY = JUMP_VELOCITY;
            jumpsUsed = 1;
            sliding = false;
            playerH = 56;
        }
    }

    private void startSlide() {
        if (playerY >= GROUND_Y - 0.5 && !sliding) {
            sliding = true;
            slideTicks = 34;
            playerH = 30;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animTick++;

        for (Cloud c : clouds) {
            c.x -= c.speed * (state == State.PLAYING ? 1.6 : 0.6);
            if (c.x < -80) {
                c.x = W + rng.nextInt(120);
                c.y = 40 + rng.nextInt(120);
            }
        }

        for (Mountain m : mountains) {
            m.x -= state == State.PLAYING ? 1.5 : 0.4;
            if (m.x < -260) {
                m.x = W + rng.nextInt(160);
                m.h = 220 + rng.nextInt(60);
            }
        }

        if (state == State.PLAYING) {
            tickPlaying();
        }

        repaint();
    }

    private void tickPlaying() {
        velY += GRAVITY;
        playerY += velY;
        if (playerY > GROUND_Y) {
            playerY = GROUND_Y;
            velY = 0;
            jumpsUsed = 0;
        }

        if (sliding) {
            slideTicks--;
            if (slideTicks <= 0) {
                sliding = false;
                playerH = 56;
            }
        }

        distance += worldSpeed;
        score = (int) (distance / 6) + coins * 10;
        worldSpeed = Math.min(18.5, 7.0 + distance / 3600.0);

        spawnCooldown--;
        if (spawnCooldown <= 0) {
            spawnObstacle();
        }

        coinCooldown--;
        if (coinCooldown <= 0) {
            spawnCoins();
        }

        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            o.x -= worldSpeed;
            if (o.x + o.w < 0) {
                obstacles.remove(i);
            }
        }

        for (int i = coinList.size() - 1; i >= 0; i--) {
            Coin c = coinList.get(i);
            c.x -= worldSpeed;
            if (c.x + 18 < 0) {
                coinList.remove(i);
            }
        }

        Rectangle pBox = playerBox();
        for (Obstacle o : obstacles) {
            if (pBox.intersects(o.box())) {
                gameOver();
                return;
            }
        }

        for (int i = coinList.size() - 1; i >= 0; i--) {
            Coin c = coinList.get(i);
            if (pBox.intersects(new Rectangle(c.x + 2, c.y + 2, 14, 14))) {
                coinList.remove(i);
                coins++;
            }
        }
    }

    private void spawnObstacle() {
        ObstacleType previousType = lastObstacleType;
        ObstacleType type = chooseObstacleType();
        int spawnX = W + 40;

        switch (type) {
            case STUMP:
                obstacles.add(new Obstacle(spawnX, GROUND_Y - 40, 36, 40, type));
                break;
            case CRATE:
                obstacles.add(new Obstacle(spawnX, GROUND_Y - 58, 42, 58, type));
                break;
            case SPIKE_ROW:
                obstacles.add(new Obstacle(spawnX, GROUND_Y - 26, 64, 26, type));
                break;
            case BARREL:
                obstacles.add(new Obstacle(spawnX, GROUND_Y - 34, 34, 34, type));
                break;
            case TOTEM:
                obstacles.add(new Obstacle(spawnX, GROUND_Y - 74, 30, 74, type));
                break;
            case LOW_SIGN:
                obstacles.add(new Obstacle(spawnX + 10, GROUND_Y - 46, 60, 20, type));
                break;
            case DRONE_HIGH:
                obstacles.add(new Obstacle(spawnX + 8, GROUND_Y - 78, 52, 30, type));
                break;
            case DRONE_LOW:
                obstacles.add(new Obstacle(spawnX + 8, GROUND_Y - 70, 56, 30, type));
                break;
        }

        obstacleCount++;
        lastObstacleType = type;
        patternStep++;
        clearCoinsInsideLatestObstacle();

        int base = 72 - (int) ((worldSpeed - 7.0) * 2.8) + rng.nextInt(14);
        base = Math.max(36, base);
        if (type == ObstacleType.CRATE || type == ObstacleType.SPIKE_ROW || type == ObstacleType.BARREL) {
            base -= 2;
        }
        if (type == ObstacleType.TOTEM) {
            base += 3;
        }
        if (type == ObstacleType.DRONE_LOW || previousType == ObstacleType.DRONE_LOW || type == ObstacleType.LOW_SIGN) {
            base += 8;
        }
        if (type == ObstacleType.DRONE_HIGH || previousType == ObstacleType.DRONE_HIGH) {
            base += 5;
        }
        if (obstacleCount < 4) {
            base += 12;
        }

        spawnCooldown = base;
    }

    private ObstacleType chooseObstacleType() {
        if (obstacleCount < 4) {
            return obstacleCount % 2 == 0 ? ObstacleType.STUMP : ObstacleType.CRATE;
        }

        ObstacleType[] earlyPool = {
            ObstacleType.STUMP, ObstacleType.STUMP, ObstacleType.CRATE,
            ObstacleType.BARREL, ObstacleType.SPIKE_ROW
        };
        ObstacleType[] midPool = {
            ObstacleType.STUMP, ObstacleType.CRATE, ObstacleType.BARREL,
            ObstacleType.SPIKE_ROW, ObstacleType.TOTEM, ObstacleType.DRONE_LOW
        };
        ObstacleType[] latePool = {
            ObstacleType.STUMP, ObstacleType.CRATE, ObstacleType.BARREL,
            ObstacleType.SPIKE_ROW, ObstacleType.TOTEM, ObstacleType.DRONE_LOW,
            ObstacleType.DRONE_HIGH, ObstacleType.LOW_SIGN
        };

        ObstacleType[] pool = distance < 1800 ? earlyPool : (distance < 4200 ? midPool : latePool);
        ObstacleType type = pool[rng.nextInt(pool.length)];

        if (type == lastObstacleType && rng.nextBoolean()) {
            type = pool[rng.nextInt(pool.length)];
        }

        if ((lastObstacleType == ObstacleType.DRONE_LOW || lastObstacleType == ObstacleType.LOW_SIGN)
            && (type == ObstacleType.DRONE_LOW || type == ObstacleType.LOW_SIGN)) {
            type = ObstacleType.CRATE;
        }

        if (patternStep % 6 == 5 && distance >= 2200) {
            return rng.nextBoolean() ? ObstacleType.DRONE_HIGH : ObstacleType.TOTEM;
        }

        return type;
    }

    private void spawnCoins() {
        int attempts = 0;
        boolean placed = false;

        while (attempts < 5 && !placed) {
            int pattern = rng.nextInt(4);
            int row = rng.nextInt(4);
            int yBase = GROUND_Y - 34 - row * 38;
            int count = 3 + rng.nextInt(4);
            int startX = W + 60 + rng.nextInt(80);
            ArrayList<Coin> preview = buildCoinPattern(startX, yBase, count, pattern);

            if (canPlaceCoinPattern(preview)) {
                coinList.addAll(preview);
                placed = true;
            }
            attempts++;
        }

        coinCooldown = 70 + rng.nextInt(55);
    }

    private ArrayList<Coin> buildCoinPattern(int startX, int yBase, int count, int pattern) {
        ArrayList<Coin> preview = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = startX + i * 28;
            int y = yBase;

            if (pattern == 1) {
                y -= (i % 2 == 0 ? 0 : 22);
            } else if (pattern == 2) {
                y -= Math.abs(2 - i) * 10;
            } else if (pattern == 3) {
                x += rng.nextInt(13) - 6;
                y -= rng.nextInt(28);
            }

            preview.add(new Coin(x, y));
        }
        return preview;
    }

    private void clearCoinsInsideLatestObstacle() {
        if (obstacles.isEmpty()) return;

        Rectangle block = obstacles.get(obstacles.size() - 1).box();
        for (int i = coinList.size() - 1; i >= 0; i--) {
            Coin c = coinList.get(i);
            Rectangle coinBox = new Rectangle(c.x + 2, c.y + 2, 14, 14);
            if (block.intersects(coinBox)) {
                coinList.remove(i);
            }
        }
    }

    private boolean canPlaceCoinPattern(ArrayList<Coin> preview) {
        for (Coin c : preview) {
            Rectangle coinBox = new Rectangle(c.x, c.y, 18, 18);
            for (Obstacle o : obstacles) {
                if (coinBox.intersects(o.box())) return false;
                if (Math.abs(c.x - o.x) < 90) return false;
            }
            if (c.y < 110 || c.y > GROUND_Y - 8) return false;
        }
        return true;
    }

    private Rectangle playerBox() {
        int hitboxY = (int) (playerY - playerH) + (sliding ? 2 : 4);
        int hitboxH = sliding ? playerH - 4 : playerH - 6;
        return new Rectangle(playerX + 6, hitboxY, playerW - 12, hitboxH);
    }

    private void gameOver() {
        state = State.GAME_OVER;
        totalCoins += coins;
        if (score > highScore) {
            highScore = score;
        }
        savePrefs();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        switch (state) {
            case MENU:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) menuIdx = (menuIdx + menuItems.length - 1) % menuItems.length;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) menuIdx = (menuIdx + 1) % menuItems.length;
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                    if (menuIdx == 0) {
                        resetRun();
                        state = State.PLAYING;
                    } else if (menuIdx == 1) {
                        state = State.CUSTOMIZE;
                    } else if (menuIdx == 2) {
                        syncMarketSelection();
                        state = State.MARKET;
                    } else {
                        System.exit(0);
                    }
                } else if (k == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
                break;

            case CUSTOMIZE:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) customizeRow = (customizeRow + 4) % 5;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) customizeRow = (customizeRow + 1) % 5;
                else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) cycle(-1);
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) cycle(1);
                else if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_ENTER) {
                    savePrefs();
                    state = State.MENU;
                }
                break;

            case MARKET:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) marketRow = (marketRow + 2) % 3;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) marketRow = (marketRow + 1) % 3;
                else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) marketCycle(-1);
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) marketCycle(1);
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) buyOrEquipCurrent();
                else if (k == KeyEvent.VK_ESCAPE) {
                    savePrefs();
                    state = State.MENU;
                }
                break;

            case PLAYING:
                if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP || k == KeyEvent.VK_W) jump();
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) startSlide();
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;

            case GAME_OVER:
                if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                    resetRun();
                    state = State.PLAYING;
                } else if (k == KeyEvent.VK_ESCAPE) {
                    state = State.MENU;
                }
                break;
        }
    }

    private void cycle(int dir) {
        if (customizeRow == 0) {
            skinIdx = (skinIdx + dir + skinColors.length) % skinColors.length;
        } else if (customizeRow == 1) {
            outfitIdx = (outfitIdx + dir + outfitColors.length) % outfitColors.length;
        } else if (customizeRow == 2) {
            hatIdx = nextOwnedIndex(hatIdx, dir, ownedHats);
        } else if (customizeRow == 3) {
            faceIdx = nextOwnedIndex(faceIdx, dir, ownedFaces);
        } else {
            backIdx = nextOwnedIndex(backIdx, dir, ownedBacks);
        }
    }

    private int nextOwnedIndex(int current, int dir, boolean[] owned) {
        int idx = current;
        for (int i = 0; i < owned.length; i++) {
            idx = (idx + dir + owned.length) % owned.length;
            if (owned[idx]) return idx;
        }
        return current;
    }

    private void marketCycle(int dir) {
        if (marketRow == 0) {
            marketHatIdx = (marketHatIdx + dir + hatNames.length) % hatNames.length;
        } else if (marketRow == 1) {
            marketFaceIdx = (marketFaceIdx + dir + faceNames.length) % faceNames.length;
        } else {
            marketBackIdx = (marketBackIdx + dir + backNames.length) % backNames.length;
        }
    }

    private void buyOrEquipCurrent() {
        if (marketRow == 0) {
            hatIdx = buyOrEquip(ownedHats, hatPrices, marketHatIdx, hatIdx);
        } else if (marketRow == 1) {
            faceIdx = buyOrEquip(ownedFaces, facePrices, marketFaceIdx, faceIdx);
        } else {
            backIdx = buyOrEquip(ownedBacks, backPrices, marketBackIdx, backIdx);
        }
        savePrefs();
    }

    private int buyOrEquip(boolean[] owned, int[] prices, int selectedIdx, int equippedIdx) {
        if (owned[selectedIdx]) {
            return selectedIdx;
        }
        if (totalCoins >= prices[selectedIdx]) {
            totalCoins -= prices[selectedIdx];
            owned[selectedIdx] = true;
            return selectedIdx;
        }
        return equippedIdx;
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        GradientPaint sky = new GradientPaint(0, 0, skyTop, 0, H, skyBottom);
        g.setPaint(sky);
        g.fillRect(0, 0, W, H);

        drawBackdrop(g);
        drawGround(g);

        switch (state) {
            case MENU:
                drawPlayer(g, true);
                drawMenu(g);
                break;
            case CUSTOMIZE:
                drawCustomize(g);
                break;
            case MARKET:
                drawMarket(g);
                break;
            case PLAYING:
                drawWorld(g);
                drawPlayer(g, false);
                drawHUD(g);
                break;
            case GAME_OVER:
                drawWorld(g);
                drawPlayer(g, false);
                drawHUD(g);
                drawGameOver(g);
                break;
        }
    }

    private void drawBackdrop(Graphics2D g) {
        g.setColor(new Color(255, 244, 194, 180));
        g.fillOval(690, 70, 118, 118);
        g.setColor(new Color(255, 232, 140, 120));
        g.fillOval(675, 55, 148, 148);

        g.setColor(new Color(0xD8A35D));
        g.fillRect(0, GROUND_Y - 22, W, 22);

        for (int x = -20; x < W; x += 120) {
            int duneOffset = (int) ((distance * 0.2 + x) % 120);
            g.setColor(new Color(0xE3BC79));
            g.fillOval(x - duneOffset, GROUND_Y - 34, 170, 44);
        }

        for (Mountain m : mountains) {
            int x = (int) m.x;
            g.setColor(new Color(0x7087A5));
            int[] xs = { x, x + 130, x + 260 };
            int[] ys = { GROUND_Y, GROUND_Y - m.h, GROUND_Y };
            g.fillPolygon(xs, ys, 3);
            g.setColor(new Color(0xF6F0E4));
            int[] sxs = { x + 110, x + 130, x + 150 };
            int[] sys = { GROUND_Y - m.h + 28, GROUND_Y - m.h, GROUND_Y - m.h + 28 };
            g.fillPolygon(sxs, sys, 3);
        }

        for (Cloud c : clouds) {
            int x = (int) c.x;
            int y = (int) c.y;
            g.setColor(new Color(255, 255, 255, 90));
            g.fillOval(x + 4, y + 4, 50, 28);
            g.fillOval(x + 29, y - 6, 45, 30);
            g.fillOval(x + 54, y + 4, 45, 26);
            g.setColor(Color.WHITE);
            g.fillOval(x, y, 50, 28);
            g.fillOval(x + 25, y - 10, 45, 30);
            g.fillOval(x + 50, y, 45, 26);
        }
    }

    private void drawGround(Graphics2D g) {
        g.setColor(new Color(0x6A9A40));
        g.fillRect(0, GROUND_Y, W, 12);
        g.setColor(new Color(0x4B7A2A));
        for (int x = 0; x < W; x += 14) {
            g.fillRect(x, GROUND_Y - ((x / 14) % 2 == 0 ? 4 : 2), 8, 4);
        }
        g.setColor(new Color(0xB77C44));
        g.fillRect(0, GROUND_Y + 12, W, H - GROUND_Y - 12);
        g.setColor(new Color(0x996337));
        int offset = (int) (distance % 32);
        for (int x = -offset; x < W; x += 32) {
            g.fillRect(x, GROUND_Y + 20, 6, 6);
            g.fillRect(x + 16, GROUND_Y + 38, 6, 6);
            g.fillRect(x + 8, GROUND_Y + 66, 6, 6);
        }
        g.setColor(new Color(0x7F502A));
        for (int x = -(int) (distance % 54); x < W; x += 54) {
            g.fillRect(x, GROUND_Y + 90, 18, 4);
        }
    }

    private void drawWorld(Graphics2D g) {
        for (Obstacle o : obstacles) {
            switch (o.type) {
                case STUMP:
                    g.setColor(new Color(0x7F512E));
                    g.fillRect(o.x, o.y, o.w, o.h);
                    g.setColor(new Color(0xA56A3B));
                    g.fillRect(o.x + 4, o.y + 5, o.w - 8, 7);
                    g.setColor(new Color(0x5B381E));
                    g.fillRect(o.x + 5, o.y + 16, 5, o.h - 18);
                    g.fillRect(o.x + o.w - 10, o.y + 16, 5, o.h - 18);
                    g.fillRect(o.x + o.w / 2 - 2, o.y + 10, 4, o.h - 12);
                    g.setColor(new Color(0x3B2513));
                    g.drawRect(o.x, o.y, o.w - 1, o.h - 1);
                    break;

                case CRATE:
                    g.setColor(new Color(0xC27B30));
                    g.fillRect(o.x, o.y, o.w, o.h);
                    g.setColor(new Color(0x8C4F16));
                    g.drawRect(o.x, o.y, o.w - 1, o.h - 1);
                    g.drawLine(o.x, o.y, o.x + o.w - 1, o.y + o.h - 1);
                    g.drawLine(o.x + o.w - 1, o.y, o.x, o.y + o.h - 1);
                    g.fillRect(o.x + 4, o.y + 4, o.w - 8, 4);
                    g.fillRect(o.x + 4, o.y + o.h - 8, o.w - 8, 4);
                    break;

                case SPIKE_ROW:
                    g.setColor(new Color(0x6E6E6E));
                    g.fillRect(o.x, o.y + o.h - 6, o.w, 6);
                    g.setColor(new Color(0xE3E3E3));
                    for (int i = 0; i < o.w; i += 10) {
                        int[] tx = { o.x + i, o.x + i + 5, o.x + i + 10 };
                        int[] ty = { o.y + o.h - 6, o.y, o.y + o.h - 6 };
                        g.fillPolygon(tx, ty, 3);
                    }
                    break;

                case BARREL:
                    g.setColor(new Color(0x8D5524));
                    g.fillOval(o.x, o.y, o.w, o.h);
                    g.setColor(new Color(0x5C3A21));
                    g.drawOval(o.x, o.y, o.w - 1, o.h - 1);
                    g.fillRect(o.x + 3, o.y + 8, o.w - 6, 4);
                    g.fillRect(o.x + 3, o.y + 22, o.w - 6, 4);
                    g.setColor(new Color(0xC68642));
                    g.fillRect(o.x + 10, o.y + 4, o.w - 20, o.h - 8);
                    break;

                case TOTEM:
                    g.setColor(new Color(0x8B5E34));
                    g.fillRect(o.x, o.y, o.w, o.h);
                    g.setColor(new Color(0x5A3A22));
                    g.drawRect(o.x, o.y, o.w - 1, o.h - 1);
                    g.setColor(new Color(0xE9C46A));
                    g.fillRect(o.x + 6, o.y + 10, o.w - 12, 8);
                    g.fillRect(o.x + 6, o.y + 32, o.w - 12, 8);
                    g.setColor(new Color(0x2A9D8F));
                    g.fillRect(o.x + 8, o.y + 52, o.w - 16, 10);
                    break;

                case LOW_SIGN:
                    g.setColor(new Color(0x6D4C41));
                    g.fillRect(o.x + 6, o.y + 16, 6, 30);
                    g.fillRect(o.x + o.w - 12, o.y + 16, 6, 30);
                    g.setColor(new Color(0xE76F51));
                    g.fillRect(o.x, o.y, o.w, 18);
                    g.setColor(Color.WHITE);
                    g.fillRect(o.x + 8, o.y + 6, o.w - 16, 3);
                    break;

                case DRONE_HIGH:
                case DRONE_LOW:
                    g.setColor(new Color(0x4A2C2A));
                    g.fillRect(o.x + 14, o.y + 10, o.w - 28, o.h - 12);
                    g.setColor(new Color(0x2D1B1A));
                    g.fillRect(o.x + 18, o.y + 8, o.w - 36, 4);
                    int wingFlap = (animTick / 4) % 2 == 0 ? -6 : 4;
                    g.setColor(new Color(0x6D3B38));
                    g.fillRect(o.x + 2, o.y + 10 + wingFlap, 18, 4);
                    g.fillRect(o.x + o.w - 20, o.y + 10 - wingFlap, 18, 4);
                    g.fillRect(o.x + 10, o.y + 18, 10, 4);
                    g.fillRect(o.x + o.w - 20, o.y + 18, 10, 4);
                    g.setColor(new Color(0xF4A261));
                    g.fillRect(o.x + o.w - 10, o.y + 13, 6, 4);
                    g.setColor(Color.WHITE);
                    g.fillRect(o.x + o.w / 2 + 4, o.y + 11, 4, 4);
                    g.setColor(Color.BLACK);
                    g.fillRect(o.x + o.w / 2 + 5, o.y + 12, 2, 2);
                    break;
            }
        }

        for (Coin c : coinList) {
            int pulse = (animTick / 6) % 4;
            int squish = pulse == 0 ? 0 : (pulse == 2 ? 4 : 2);
            g.setColor(new Color(0xFFD700));
            g.fillOval(c.x + squish / 2, c.y, 18 - squish, 18);
            g.setColor(new Color(0xB8860B));
            g.fillRect(c.x + 7, c.y + 4, 4, 10);
        }
    }

    private void drawPlayer(Graphics2D g, boolean idle) {
        drawPlayer(g, idle, hatIdx, faceIdx, backIdx);
    }

    private Rectangle standingHeadBox(int x, int y, int h) {
        return new Rectangle(x + 4, y - h, playerW - 8, 18);
    }

    private Rectangle slidingHeadBox(int x, int y, int h) {
        return new Rectangle(x + playerW - 16, y - h, 16, 14);
    }

    private Rectangle bodyBox(int x, int y, int h, boolean isSliding) {
        if (isSliding) {
            return new Rectangle(x, y - h, playerW, h);
        }
        return new Rectangle(x + 4, y - h + 18, playerW - 8, h - 30);
    }

    private void drawPlayer(Graphics2D g, boolean idle, int hatChoice, int faceChoice, int backChoice) {
        int x = playerX;
        int y = (int) playerY;
        int h = playerH;
        int bob = idle ? (int) (Math.sin(animTick * 0.1) * 2) : 0;
        y += bob;

        Color skin = skinColors[skinIdx];
        Color outfit = outfitColors[outfitIdx];
        Color outfitDark = outfit.darker();

        Rectangle body = bodyBox(x, y, h, sliding);
        Rectangle head = sliding ? slidingHeadBox(x, y, h) : standingHeadBox(x, y, h);

        drawBackAccessory(g, body, head, backChoice);

        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 2, GROUND_Y + 4, playerW + 4, 8);

        if (sliding) {
            g.setColor(outfit);
            g.fillRect(x, y - h, playerW, h);
            g.setColor(outfitDark);
            g.fillRect(x + 6, y - h + 8, playerW - 12, 4);
            g.setColor(skin);
            g.fillRect(x + playerW - 16, y - h, 16, 14);
            drawHat(g, head, hatChoice);
            drawFaceAccessory(g, head, faceChoice);
            return;
        }

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

        g.setColor(outfit);
        g.fillRect(x + 4, y - h + 18, playerW - 8, h - 30);
        g.setColor(outfit.brighter());
        g.fillRect(x + 9, y - h + 22, playerW - 18, 10);
        g.setColor(outfitDark);
        g.fillRect(x + 4, y - h + 30, playerW - 8, 4);
        g.fillRect(x + 11, y - h + 18, 4, 8);
        g.fillRect(x + playerW - 15, y - h + 18, 4, 8);

        g.setColor(skin);
        int armSwing = idle ? 0 : (legPhase == 0 ? -3 : 3);
        g.fillRect(x - 2, y - h + 22 + armSwing, 8, 18);
        g.fillRect(x + playerW - 6, y - h + 22 - armSwing, 8, 18);

        g.setColor(skin);
        g.fillRect(x + 4, y - h, playerW - 8, 18);
        g.setColor(new Color(0xE59866));
        g.fillRect(x + 6, y - h + 2, playerW - 12, 4);
        g.setColor(Color.WHITE);
        g.fillRect(x + 22, y - h + 6, 6, 6);
        g.setColor(Color.BLACK);
        g.fillRect(x + 24, y - h + 8, 3, 3);
        g.fillRect(x + 11, y - h + 8, 2, 2);
        g.setColor(new Color(0x6B3A1A));
        g.fillRect(x + 18, y - h + 14, 8, 2);

        drawHat(g, head, hatChoice);
        drawFaceAccessory(g, head, faceChoice);
    }

    private void drawHat(Graphics2D g, Rectangle head, int hatChoice) {
        int hx = head.x;
        int hy = head.y;
        int hw = head.width;
        switch (hatChoice) {
            case 0:
                return;
            case 1:
                g.setColor(new Color(0xD62828));
                g.fillRect(hx - 2, hy - 6, hw + 4, 6);
                g.fillRect(hx + hw - 6, hy - 4, 14, 4);
                g.setColor(Color.WHITE);
                g.fillRect(hx + hw / 2 - 3, hy - 4, 6, 2);
                break;
            case 2:
                g.setColor(new Color(0xFFD700));
                g.fillRect(hx, hy - 8, hw, 4);
                int[] cx = { hx, hx + 4, hx + 8, hx + hw / 2 - 4, hx + hw / 2, hx + hw / 2 + 4, hx + hw - 8, hx + hw - 4, hx + hw };
                int[] cy = { hy - 8, hy - 14, hy - 8, hy - 8, hy - 14, hy - 8, hy - 8, hy - 14, hy - 8 };
                g.fillPolygon(cx, cy, 9);
                g.setColor(new Color(0xC8102E));
                g.fillRect(hx + hw / 2 - 2, hy - 12, 4, 4);
                break;
            case 3:
                g.setColor(new Color(0x4A4A4A));
                g.fillRect(hx - 2, hy - 8, hw + 4, 10);
                g.setColor(new Color(0x2A2A2A));
                g.fillRect(hx - 2, hy - 2, hw + 4, 4);
                g.setColor(new Color(0xFFD166));
                g.fillRect(hx + hw / 2 - 2, hy - 8, 4, 4);
                break;
            case 4:
                g.setColor(new Color(0xE63946));
                g.fillRect(hx + hw / 2 - 2, hy - 10, 4, 10);
                g.fillRect(hx + hw / 2 - 4, hy - 8, 8, 6);
                break;
            case 5:
                g.setColor(new Color(0x8D99AE));
                g.fillRect(hx - 1, hy - 8, hw + 2, 8);
                g.fillRect(hx + 2, hy - 12, hw - 4, 4);
                g.setColor(new Color(0xEDF2F4));
                g.fillRect(hx + 5, hy - 10, hw - 10, 2);
                break;
            case 6:
                g.setColor(new Color(0xD90429));
                g.fillRect(hx + 1, hy - 10, 4, 10);
                g.fillRect(hx + hw - 5, hy - 10, 4, 10);
                g.setColor(new Color(0x6A040F));
                g.fillRect(hx + 1, hy - 12, 4, 3);
                g.fillRect(hx + hw - 5, hy - 12, 4, 3);
                break;
        }
    }

    private void drawFaceAccessory(Graphics2D g, Rectangle head, int faceChoice) {
        int hx = head.x;
        int hy = head.y;
        int hw = head.width;
        switch (faceChoice) {
            case 1:
                g.setColor(new Color(0x222222));
                if (hw <= 16) {
                    g.fillRect(hx + 1, hy + 4, 5, 4);
                    g.fillRect(hx + hw - 6, hy + 4, 5, 4);
                    g.fillRect(hx + 6, hy + 5, Math.max(2, hw - 12), 1);
                    g.setColor(new Color(0x8ECAE6));
                    g.fillRect(hx + 2, hy + 5, 3, 1);
                    g.fillRect(hx + hw - 5, hy + 5, 3, 1);
                    break;
                }
                g.fillRect(hx + 3, hy + 5, 7, 5);
                g.fillRect(hx + hw - 10, hy + 5, 7, 5);
                g.fillRect(hx + 10, hy + 6, hw - 20, 2);
                g.setColor(new Color(0x8ECAE6));
                g.fillRect(hx + 4, hy + 6, 5, 2);
                g.fillRect(hx + hw - 9, hy + 6, 5, 2);
                break;
            case 2:
                g.setColor(new Color(0x4ECDC4));
                g.fillRect(hx + 3, hy + head.height - 4, Math.max(8, hw - 6), 4);
                g.setColor(new Color(0x2A9D8F));
                g.fillRect(hx + 1, hy + head.height - 4, 2, 3);
                g.fillRect(hx + hw - 3, hy + head.height - 4, 2, 3);
                break;
            case 3:
                g.setColor(new Color(0x9BF6FF));
                g.fillRect(hx + 2, hy + 4, hw - 4, Math.min(6, head.height - 5));
                g.setColor(new Color(0x4361EE));
                g.drawRect(hx + 2, hy + 4, hw - 5, Math.min(5, head.height - 6));
                break;
            case 4:
                g.setColor(new Color(0xE63946));
                g.fillRect(hx - 1, hy + 1, hw + 2, 3);
                break;
            case 5:
                g.setColor(new Color(0xC0C0C0));
                g.fillRect(hx + hw - Math.min(11, hw - 2), hy + 5, Math.min(7, hw - 4), Math.min(7, head.height - 6));
                g.setColor(Color.BLACK);
                g.drawRect(hx + hw - Math.min(11, hw - 2), hy + 5, Math.min(6, hw - 5), Math.min(6, head.height - 7));
                g.drawLine(hx + hw - 5, hy + 8, hx + hw + 1, hy + 8);
                break;
            case 6:
                g.setColor(new Color(0x9D0208));
                g.fillRect(hx + 3, hy + head.height - 5, Math.max(8, hw - 6), 4);
                g.setColor(new Color(0xE63946));
                g.fillRect(hx + 5, hy + head.height - 4, Math.max(4, hw - 10), 1);
                break;
        }
    }

    private void drawBackAccessory(Graphics2D g, Rectangle body, Rectangle head, int backChoice) {
        int left = body.x;
        int top = body.y;
        int right = body.x + body.width;
        int centerX = left + body.width / 2;
        switch (backChoice) {
            case 1:
                g.setColor(new Color(0xD62828));
                g.fillRect(left - 10, top + 2, 10, 24);
                g.fillPolygon(new int[] { left - 10, left - 22, left - 10 }, new int[] { top + 2, top + 16, top + 26 }, 3);
                break;
            case 2:
                g.setColor(new Color(0x6C757D));
                g.fillRect(left - 10, top, 10, 20);
                g.setColor(new Color(0xF77F00));
                g.fillRect(left - 8, top + 20, 3, 10);
                g.fillRect(left - 3, top + 20, 3, 10);
                g.setColor(new Color(0xADB5BD));
                g.fillRect(left - 9, top + 4, 8, 8);
                break;
            case 3:
                g.setColor(new Color(0xF4F1DE));
                g.fillPolygon(new int[] { left + 2, left - 18, left - 6 }, new int[] { top + 6, top - 6, top + 16 }, 3);
                g.fillPolygon(new int[] { right - 2, right + 18, right + 6 }, new int[] { top + 6, top - 6, top + 16 }, 3);
                break;
            case 4:
                g.setColor(new Color(0xFFB703));
                g.fillRect(left - 12, top, 8, 28);
                g.setColor(new Color(0xFB8500));
                g.fillRect(left - 4, top, 12, 8);
                break;
            case 5:
                g.setColor(new Color(0x5E548E));
                g.fillRect(left - 10, top, 12, 18);
                g.setColor(new Color(0x9F86C0));
                g.fillRect(left - 8, top + 4, 8, 10);
                g.setColor(new Color(0x231942));
                g.fillRect(left - 6, top + 16, 4, 2);
                break;
            case 6:
                g.setColor(new Color(0xF8E16C));
                g.fillOval(centerX - 12, head.y - 10, 24, 8);
                g.setColor(new Color(0xFFF3B0));
                g.fillOval(centerX - 7, head.y - 8, 14, 4);
                break;
        }
    }

    private void drawHUD(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(16, 14, 380, 64, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(pixelFont(18));
        g.drawString("SCORE  " + score, 32, 38);
        g.drawString("COINS  " + coins, 32, 62);
        g.setFont(pixelFont(14));
        g.drawString("HI " + Math.max(highScore, score), 180, 38);
        g.drawString("SPD " + String.format("%.1f", worldSpeed), 180, 62);
        g.drawString("BANK " + totalCoins, 300, 38);
    }

    private void drawMenu(Graphics2D g) {
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

        g.setFont(pixelFont(28));
        for (int i = 0; i < menuItems.length; i++) {
            String s = (menuIdx == i ? "> " : "  ") + menuItems[i] + (menuIdx == i ? " <" : "  ");
            int x = (W - g.getFontMetrics().stringWidth(s)) / 2;
            g.setColor(menuIdx == i ? new Color(0xFFD166) : Color.WHITE);
            g.drawString(s, x, 246 + i * 48);
        }

        g.setFont(pixelFont(18));
        g.setColor(new Color(0xF4F1DE));
        g.drawString("BANK  " + totalCoins, 32, 42);

        g.setFont(pixelFont(14));
        g.setColor(new Color(255, 255, 255, 200));
        String hint = "UP/DOWN navigate | ENTER select | SPACE jump | DOWN slide | ESC quit";
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

        g.setFont(pixelFont(18));
        g.setColor(Color.WHITE);
        g.drawString("BANK  " + totalCoins, 54, 110);

        drawPlayer(g, true);

        int px = 460;
        int py = 120;
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(px, py, 460, 330, 16, 16);

        String[] labels = { "SKIN", "OUTFIT", "HAT", "FACE", "BACK" };
        String[] values = {
            "TONE " + (skinIdx + 1),
            "COLOR " + (outfitIdx + 1),
            hatNames[hatIdx],
            faceNames[faceIdx],
            backNames[backIdx]
        };

        for (int i = 0; i < 5; i++) {
            int rowY = py + 42 + i * 58;
            g.setFont(pixelFont(20));
            g.setColor(customizeRow == i ? new Color(0xFFD166) : Color.WHITE);
            g.drawString((customizeRow == i ? "> " : "  ") + labels[i], px + 24, rowY);
            g.drawString("<", px + 200, rowY);
            g.drawString(">", px + 420, rowY);

            if (i == 0) {
                g.setColor(skinColors[skinIdx]);
                g.fillRoundRect(px + 240, rowY - 24, 160, 32, 8, 8);
            } else if (i == 1) {
                g.setColor(outfitColors[outfitIdx]);
                g.fillRoundRect(px + 240, rowY - 24, 160, 32, 8, 8);
            } else {
                drawValueTag(g, values[i], px, rowY);
            }
        }

        g.setFont(pixelFont(14));
        g.setColor(new Color(255, 255, 255, 200));
        String hint = "UP/DOWN row | LEFT/RIGHT change owned gear | ENTER save & back";
        g.drawString(hint, (W - g.getFontMetrics().stringWidth(hint)) / 2, H - 30);
    }

    private void drawValueTag(Graphics2D g, String value, int px, int rowY) {
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRoundRect(px + 240, rowY - 24, 160, 32, 8, 8);
        g.setFont(pixelFont(18));
        g.setColor(Color.WHITE);
        g.drawString(value, px + 240 + (160 - g.getFontMetrics().stringWidth(value)) / 2, rowY);
    }

    private void drawMarket(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 95));
        g.fillRect(0, 0, W, H);

        g.setFont(pixelFont(42));
        g.setColor(new Color(0xFFD166));
        String title = "MARKET";
        g.drawString(title, (W - g.getFontMetrics().stringWidth(title)) / 2, 78);

        g.setFont(pixelFont(18));
        g.setColor(Color.WHITE);
        g.drawString("BANK  " + totalCoins, 54, 110);

        drawPlayer(g, true, marketHatIdx, marketFaceIdx, marketBackIdx);

        int px = 420;
        int py = 140;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(px, py, 500, 250, 16, 16);

        String[] labels = { "HAT", "FACE", "BACK" };
        String[] values = { hatNames[marketHatIdx], faceNames[marketFaceIdx], backNames[marketBackIdx] };
        int[] prices = { hatPrices[marketHatIdx], facePrices[marketFaceIdx], backPrices[marketBackIdx] };
        boolean[] owned = { ownedHats[marketHatIdx], ownedFaces[marketFaceIdx], ownedBacks[marketBackIdx] };

        for (int i = 0; i < 3; i++) {
            int rowY = py + 58 + i * 64;
            g.setFont(pixelFont(20));
            g.setColor(marketRow == i ? new Color(0xFFD166) : Color.WHITE);
            g.drawString((marketRow == i ? "> " : "  ") + labels[i], px + 20, rowY);
            g.drawString("<", px + 170, rowY);
            g.drawString(">", px + 448, rowY);

            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(px + 200, rowY - 24, 160, 32, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(pixelFont(16));
            g.drawString(values[i], px + 200 + (160 - g.getFontMetrics().stringWidth(values[i])) / 2, rowY - 2);

            String badge = owned[i] ? "OWNED" : prices[i] + " COINS";
            g.setColor(owned[i] ? new Color(0x2A9D8F) : new Color(0xE76F51));
            g.fillRoundRect(px + 378, rowY - 24, 96, 32, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(badge, px + 378 + (96 - g.getFontMetrics().stringWidth(badge)) / 2, rowY - 2);
        }

        g.setFont(pixelFont(14));
        g.setColor(new Color(255, 255, 255, 210));
        String hint = "UP/DOWN row | LEFT/RIGHT browse | ENTER buy | ESC back";
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
        String b = "BANK  " + totalCoins;
        g.drawString(b, (W - g.getFontMetrics().stringWidth(b)) / 2, 308);

        g.setFont(pixelFont(20));
        g.setColor(new Color(0xFFD700));
        String hi = (score >= highScore && score > 0 ? "NEW HIGH SCORE!  " : "HIGH SCORE  ") + highScore;
        g.drawString(hi, (W - g.getFontMetrics().stringWidth(hi)) / 2, 344);

        g.setFont(pixelFont(18));
        g.setColor(Color.WHITE);
        String h1 = "ENTER  Run again";
        String h2 = "ESC    Main menu";
        g.drawString(h1, (W - g.getFontMetrics().stringWidth(h1)) / 2, 390);
        g.drawString(h2, (W - g.getFontMetrics().stringWidth(h2)) / 2, 418);
    }

    private Font pixelFont(int size) {
        return new Font(Font.MONOSPACED, Font.BOLD, size);
    }

    static class Obstacle {
        int x;
        int y;
        int w;
        int h;
        ObstacleType type;

        Obstacle(int x, int y, int w, int h, ObstacleType type) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.type = type;
        }

        Rectangle box() {
            switch (type) {
                case STUMP:
                    return new Rectangle(x + 3, y + 4, w - 6, h - 4);
                case CRATE:
                    return new Rectangle(x + 4, y + 4, w - 8, h - 4);
                case SPIKE_ROW:
                    return new Rectangle(x + 6, y + 3, w - 12, h - 3);
                case BARREL:
                    return new Rectangle(x + 4, y + 3, w - 8, h - 4);
                case TOTEM:
                    return new Rectangle(x + 3, y + 4, w - 6, h - 4);
                case LOW_SIGN:
                    return new Rectangle(x + 2, y + 1, w - 4, h - 1);
                case DRONE_HIGH:
                case DRONE_LOW:
                    return new Rectangle(x + 6, y + 4, w - 12, h - 8);
                default:
                    return new Rectangle(x, y, w, h);
            }
        }
    }

    static class Coin {
        int x;
        int y;

        Coin(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Cloud {
        double x;
        double y;
        double speed;

        Cloud(double x, double y, double s) {
            this.x = x;
            this.y = y;
            this.speed = s;
        }
    }

    static class Mountain {
        double x;
        int h;

        Mountain(double x, int h) {
            this.x = x;
            this.h = h;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Pixel Dash - Endless Runner");
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