package ru.mikhailche.snake;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class Snake extends JPanel implements Runnable, KeyListener {
	private static final long serialVersionUID = 1L;
	int snakeFieldWidth = 170 / 2;
	int snakeFieldHeight = 100 / 2;

	public HashMap<String, Integer> scoreboard = null;
	private final String scbfile = "scoreboard.brd";

	public void loadScoreboard() {
		File scb = new File(scbfile);
		if (scb.exists()) {
			FileInputStream fis = null;
			ObjectInputStream ois = null;
			try {
				fis = new FileInputStream(scb);
				ois = new ObjectInputStream(fis);
				Object o = ois.readObject();
				if (o instanceof HashMap<?, ?>) {
					scoreboard = castHashMap(o);
				}
			} catch (Exception e) {
				scoreboard = new HashMap<String, Integer>();
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (ois != null) {
					try {
						ois.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

		} else {
			scoreboard = new HashMap<String, Integer>();
		}
	}

	@SuppressWarnings("unchecked")
	private HashMap<String, Integer> castHashMap(Object o) {
		return (HashMap<String, Integer>) o;
	}

	public void saveScoreboard() {
		File scb = new File(scbfile);

		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			fos = new FileOutputStream(scb);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(scoreboard);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public void addScoreboard(String name, int score) {
		if (scoreboard == null) {
			loadScoreboard();
		}
		if (scoreboard.get(name) != null) {
			if (scoreboard.get(name) > score) {
				saveScoreboard();
				return;
			}
		}
		scoreboard.put(name, score);
		saveScoreboard();
	}

	public boolean playSound = false;
	Thread soundThread = new Thread(new Runnable() {
		public byte[] generateWave(double Hz, double length, AudioFormat format) {
			if (format.getChannels() > 1) {
				throw new IllegalArgumentException("only mono allowed");
			}
			byte[] buffer = new byte[(int) (format.getFrameRate() * length)];
			for (int i = 0; i < buffer.length; i++) {
				double sin = Math.sin(2.0 * Math.PI * Hz * i
						/ format.getFrameRate()) * 16;
				if (sin > Byte.MAX_VALUE) {
					buffer[i] = Byte.MAX_VALUE;

				} else if (sin < Byte.MIN_VALUE) {
					buffer[i] = Byte.MIN_VALUE;
				} else {
					buffer[i] = (byte) sin;
				}
			}
			return buffer;
		}

		public byte[] getPopSound(AudioFormat format) {
			byte[] pip = generateWave(440, 0.1, format);
			byte[] pop = generateWave(600, 0.1, format);
			byte[] buf = new byte[pip.length + pop.length];
			for (int i = 0; i < pip.length; i++) {
				buf[i] = pip[i];
			}
			for (int i = 0; i < pop.length; i++) {
				buf[i + pip.length] = pop[i];
			}
			return buf;
		}

		AudioFormat format = new AudioFormat(8000, 8, 1, true, true);

		public void run() {
			try {
				SourceDataLine sdl = AudioSystem.getSourceDataLine(format);
				sdl.open();
				sdl.start();
				while (true) {
					if (playSound) {
						byte[] buffer = getPopSound(format);
						sdl.start();
						sdl.write(buffer, 0, buffer.length);
						sdl.drain();
						sdl.stop();
						playSound = false;
					}
					Thread.yield();
				}
			} catch (LineUnavailableException e) {
				e.printStackTrace();
			}
		}
	});

	public class Point {
		int x = 0;
		int y = 0;

		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public Point() {
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Point) {
				Point p = (Point) o;
				if (this.x == p.x && this.y == p.y) {
					return true;
				}
			}

			return false;
		}

	}

	public void initializeNewGame() {
		snake.clear();
		food.clear();
		snake.add(new Point());
		snake.add(new Point());
		snake.add(new Point());
		newHeadPoint = new Point();
		points = 0;
	}

	public void showRecordTable() {
		if (scoreboard == null) {
			loadScoreboard();
		}
		Vector<String> names = new Vector<String>(scoreboard.keySet());
		String message = "Рекордсмены: \r\n";
		for (int i = 0; i < names.size(); i++) {
			message += names.get(i) + ": " + scoreboard.get(names.get(i))
					+ "\r\n";
		}
		JOptionPane.showMessageDialog(null, message, "Таблица рекордов",
				JOptionPane.INFORMATION_MESSAGE);
	}

	LinkedList<Point> snake = new LinkedList<Point>();
	LinkedList<Point> food = new LinkedList<Point>();
	int points = 0;
	Point newHeadPoint = new Point();

	public enum direction {
		NORTH, EAST, SOUTH, WEST
	}

	private direction currentSnakeDirection = direction.NORTH;
	private direction desiredSnakeDirection = direction.NORTH;

	public Snake() {
		soundThread.start();
		setDoubleBuffered(true);
		resizeGame();
	}

	public void resizeGame() {
		snakeFieldWidth = (int) (getWidth() / 15.0);
		snakeFieldHeight = (int) (getHeight() / 15.0);
		setPreferredSize(new Dimension((int) (snakeFieldWidth * 15.0),
				(int) (snakeFieldHeight * 15.0)));
	}

	public void paint(Graphics d) {
		Graphics2D g = (Graphics2D) d;
		g.setColor(Color.GREEN);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(Color.BLACK);
		for (int i = 0; i < snake.size(); i++) {
			g.setColor(new Color((int) (128.0 * (double) i / (double) snake
					.size()),
					(int) (128.0 * (double) i / (double) snake.size()),
					(int) (128.0 * (double) i / (double) snake.size())));
			g.drawRect((snake.get(i).x % snakeFieldWidth) * getWidth()
					/ snakeFieldWidth + 1, (snake.get(i).y % snakeFieldHeight)
					* getHeight() / snakeFieldHeight + 1, getWidth()
					/ snakeFieldWidth - 2, getHeight() / snakeFieldHeight - 2);
		}
		g.setColor(Color.RED);
		for (int i = 0; i < food.size(); i++) {
			g.fillOval(food.get(i).x * getWidth() / snakeFieldWidth,
					food.get(i).y * getHeight() / snakeFieldHeight, getWidth()
							/ snakeFieldWidth, getHeight() / snakeFieldHeight);
		}
		g.setColor(Color.BLACK);
		g.setFont(new Font("Arial", Font.BOLD, 20));
		g.drawString(points + "", 0, 20);
	}

	public void run() {
		while (true) {
			gameCycle();
		}
	}

	private boolean pause = false;

	public void gameCycle() {
		while (pause) {
			try {
				synchronized (this) {
					this.wait();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		moveSnake();
		digestFood();
		placeFood();
		repaint();

		try {
			Thread.sleep(30);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void moveSnake() {
		applyDesiredSnakeDirection();
		if (currentSnakeDirection.equals(direction.NORTH)) {
			newHeadPoint.x = (snake.peek().x + snakeFieldWidth)
					% snakeFieldWidth;
			newHeadPoint.y = (snake.peek().y - 1 + snakeFieldHeight)
					% snakeFieldHeight;
		} else if (currentSnakeDirection.equals(direction.EAST)) {
			newHeadPoint.x = (snake.peek().x + 1 + snakeFieldWidth)
					% snakeFieldWidth;
			newHeadPoint.y = (snake.peek().y + snakeFieldHeight)
					% snakeFieldHeight;

		} else if (currentSnakeDirection.equals(direction.SOUTH)) {
			newHeadPoint.x = (snake.peek().x + snakeFieldWidth)
					% snakeFieldWidth;
			newHeadPoint.y = (snake.peek().y + 1 + snakeFieldHeight)
					% snakeFieldHeight;

		} else if (currentSnakeDirection.equals(direction.WEST)) {
			newHeadPoint.x = (snake.peek().x - 1 + snakeFieldWidth)
					% snakeFieldWidth;
			newHeadPoint.y = (snake.peek().y + snakeFieldHeight)
					% snakeFieldHeight;

		}
		moveToHead();
	}

	public void moveToHead() {
		if (snake.contains(newHeadPoint)) {
			Object o = JOptionPane.showInputDialog("Ваше имя:");
			if (o instanceof String) {
				addScoreboard((String) o, points);
				showRecordTable();
			}
			initializeNewGame();
			return;
		}
		for (int i = snake.size() - 1; i > 0; i--) {
			snake.get(i).x = snake.get(i - 1).x;
			snake.get(i).y = snake.get(i - 1).y;
		}
		snake.getFirst().x = newHeadPoint.x;
		snake.getFirst().y = newHeadPoint.y;
	}

	public void placeFood() {
		Random r = new Random();
		if (food.isEmpty()) {
			food.add(new Point(r.nextInt(snakeFieldWidth), r
					.nextInt(snakeFieldHeight)));
		}
	}

	public void digestFood() {
		if (food.contains(snake.getFirst())) {

			Point newTail = new Point();
			Point tail = snake.getLast();
			newTail.x = tail.x;
			newTail.y = tail.y;
			snake.add(newTail);
			food.clear();
			playSound = true;
			points++;
		}
	}

	public void setDirection(direction name) {
		if (currentSnakeDirection.equals(direction.NORTH)) {
			if (name != direction.SOUTH) {
				desiredSnakeDirection = name;
			}
		} else if (currentSnakeDirection.equals(direction.EAST)) {
			if (name != direction.WEST) {
				desiredSnakeDirection = name;
			}
		} else if (currentSnakeDirection.equals(direction.SOUTH)) {
			if (name != direction.NORTH) {
				desiredSnakeDirection = name;
			}
		} else if (currentSnakeDirection.equals(direction.WEST)) {
			if (name != direction.EAST) {
				desiredSnakeDirection = name;
			}
		}
	}

	public void applyDesiredSnakeDirection() {
		currentSnakeDirection = desiredSnakeDirection;
	}

	@Override
	public void keyTyped(KeyEvent arg0) {
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_LEFT
				|| e.getKeyCode() == KeyEvent.VK_A) {
			setDirection(direction.WEST);
		} else if (e.getKeyCode() == KeyEvent.VK_RIGHT
				|| e.getKeyCode() == KeyEvent.VK_D) {
			setDirection(direction.EAST);
		} else if (e.getKeyCode() == KeyEvent.VK_UP
				|| e.getKeyCode() == KeyEvent.VK_W) {
			setDirection(direction.NORTH);
		} else if (e.getKeyCode() == KeyEvent.VK_DOWN
				|| e.getKeyCode() == KeyEvent.VK_S) {
			setDirection(direction.SOUTH);
		} else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			resizeGame();
			initializeNewGame();
		} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			pause = !pause;
			synchronized (this) {
				this.notify();
			}
		}
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame("Snake");
		final Snake snake = new Snake();
		snake.addKeyListener(snake);
		snake.setFocusable(true);

		frame.add(snake);
		JMenuBar bar = new JMenuBar();
		JMenuItem item = new JMenuItem("Таблица рекордов");
		item.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				snake.showRecordTable();
			}
		});
		bar.add(item);
		frame.setJMenuBar(bar);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		frame.pack();
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		frame.setVisible(true);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		snake.resizeGame();
		snake.initializeNewGame();

		snake.loadScoreboard();
		snake.run();

	}
}
