package ru.samsung.gamestudio;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MyGdxGame extends ApplicationAdapter {

	// --- Константы экрана (Адаптивность) ---
	public static final int SCR_WIDTH = 1280;
	public static final int SCR_HEIGHT = 720;

	// --- Состояния игры ---
	enum State { MENU, PLAYING, GAME_OVER }
	State state = State.MENU;

	// --- Основные инструменты отрисовки ---
	SpriteBatch batch;
	Viewport viewport;
	BitmapFont font;
	Vector3 touchPoint; // Для правильного перевода кликов

	// --- Ресурсы (Текстуры) ---
	Texture[] birdTextures;
	Texture tubeTexture;
	Texture tubeFlippedTexture;
	Texture gameBgTexture;
	Texture restartBgTexture;
	Texture buttonTexture;

	// --- Анимации ---
	Animation<TextureRegion> flyAnimation;
	Animation<TextureRegion> fallAnimation;

	// --- Игровые объекты и физика ---
	Rectangle bird;
	Array<Rectangle> bottomTubes; // Храним только нижние трубы
	Array<Boolean> tubeScored;    // Флаги для начисления очков

	float birdVelocity = 0;
	final float GRAVITY = -1400f;
	final float JUMP_FORCE = 450f;
	final float TUBE_WIDTH = 130f;
	final float TUBE_GAP = 260f; // Неизменное расстояние между трубами
	final float TUBE_SPEED = 300f;

	// --- Таймеры и счет ---
	float birdAnimationTime = 0;
	float tubeSpawnTimer = 0;
	int score = 0;

	// --- Кнопка в меню ---
	Rectangle playButton;

	@Override
	public void create() {
		batch = new SpriteBatch();
		viewport = new FitViewport(SCR_WIDTH, SCR_HEIGHT);
		touchPoint = new Vector3();

		// Настройка шрифта
		font = new BitmapFont();
		font.getData().setScale(4f);
		font.setColor(Color.WHITE);

		// --- ЗАГРУЗКА РЕСУРСОВ ---
		birdTextures = new Texture[3];
		birdTextures[0] = new Texture("bird/bird0.png");
		birdTextures[1] = new Texture("bird/bird1.png");
		birdTextures[2] = new Texture("bird/bird2.png");

		tubeTexture = new Texture("tube/tube.png");
		tubeFlippedTexture = new Texture("tube/tube_flipped.png");
		gameBgTexture = new Texture("background/game_bg.png");
		restartBgTexture = new Texture("background/restart_bg.png");
		buttonTexture = new Texture("button/button_bg.png");

		// --- НАСТРОЙКА АНИМАЦИЙ ---
		Array<TextureRegion> flyFrames = new Array<>();
		flyFrames.add(new TextureRegion(birdTextures[1]));
		flyFrames.add(new TextureRegion(birdTextures[2]));
		flyAnimation = new Animation<>(0.1f, flyFrames, Animation.PlayMode.LOOP);

		Array<TextureRegion> fallFrames = new Array<>();
		fallFrames.add(new TextureRegion(birdTextures[1]));
		fallFrames.add(new TextureRegion(birdTextures[0]));
		fallAnimation = new Animation<>(0.15f, fallFrames, Animation.PlayMode.LOOP);

		// --- ИНИЦИАЛИЗАЦИЯ ОБЪЕКТОВ ---
		bird = new Rectangle(250, SCR_HEIGHT / 2f, 80, 60);
		bottomTubes = new Array<>();
		tubeScored = new Array<>();

		float btnWidth = 300, btnHeight = 120;
		playButton = new Rectangle((SCR_WIDTH - btnWidth) / 2f, (SCR_HEIGHT - btnHeight) / 2f - 50, btnWidth, btnHeight);
	}

	@Override
	public void render() {
		float delta = Gdx.graphics.getDeltaTime();

		// --- ЛОГИКА КЛИКОВ ---
		if (state == State.MENU) {
			if (Gdx.input.justTouched()) {
				// Перевод экранных координат мыши в координаты игры
				touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0);
				viewport.unproject(touchPoint);

				// Проверка попадания в кнопку
				if (playButton.contains(touchPoint.x, touchPoint.y)) {
					state = State.PLAYING;
					resetGame();
				}
			}
		} else if (state == State.PLAYING) {
			updatePlaying(delta);
		} else if (state == State.GAME_OVER) {
			if (Gdx.input.justTouched()) {
				state = State.MENU;
			}
		}

		// --- ОТРИСОВКА ---
		ScreenUtils.clear(0, 0, 0, 1);

		viewport.apply();
		batch.setProjectionMatrix(viewport.getCamera().combined);

		batch.begin();

		if (state == State.MENU) {
			batch.draw(gameBgTexture, 0, 0, SCR_WIDTH, SCR_HEIGHT);
			batch.draw(buttonTexture, playButton.x, playButton.y, playButton.width, playButton.height);
			batch.draw(birdTextures[1], bird.x, bird.y, bird.width, bird.height);
		}
		else if (state == State.PLAYING) {
			batch.draw(gameBgTexture, 0, 0, SCR_WIDTH, SCR_HEIGHT);
			drawPlaying();
		}
		else if (state == State.GAME_OVER) {
			batch.draw(restartBgTexture, 0, 0, SCR_WIDTH, SCR_HEIGHT);
			font.draw(batch, "GAME OVER", SCR_WIDTH / 2f - 140, SCR_HEIGHT / 2f + 150);
			font.draw(batch, "Score: " + score, SCR_WIDTH / 2f - 100, SCR_HEIGHT / 2f + 50);
			font.draw(batch, "Tap to go to MENU", SCR_WIDTH / 2f - 190, SCR_HEIGHT / 2f - 100);
		}

		batch.end();
	}

	private void updatePlaying(float delta) {
		// Прыжок при нажатии
		if (Gdx.input.justTouched()) {
			birdVelocity = JUMP_FORCE;
			birdAnimationTime = 0;
		}

		// Физика
		birdVelocity += GRAVITY * delta;
		bird.y += birdVelocity * delta;
		birdAnimationTime += delta;

		// Проверка границ
		if (bird.y < 0 || bird.y + bird.height > SCR_HEIGHT) {
			state = State.GAME_OVER;
			return;
		}

		// Генерация труб
		tubeSpawnTimer += delta;
		if (tubeSpawnTimer > 1.5f) {
			float gapY = MathUtils.random(100, (int)(SCR_HEIGHT - TUBE_GAP - 100));
			bottomTubes.add(new Rectangle(SCR_WIDTH, 0, TUBE_WIDTH, gapY));
			tubeScored.add(false);
			tubeSpawnTimer = 0;
		}

		// Движение и коллизии труб
		for (int i = 0; i < bottomTubes.size; i++) {
			Rectangle bottomTube = bottomTubes.get(i);
			bottomTube.x -= TUBE_SPEED * delta;

			Rectangle topTube = new Rectangle(bottomTube.x, bottomTube.height + TUBE_GAP, TUBE_WIDTH, SCR_HEIGHT - bottomTube.height - TUBE_GAP);

			// Проверка столкновения
			if (bottomTube.overlaps(bird) || topTube.overlaps(bird)) {
				state = State.GAME_OVER;
				return;
			}

			// Начисление очков
			if (bottomTube.x + TUBE_WIDTH < bird.x && !tubeScored.get(i)) {
				score++;
				tubeScored.set(i, true);
			}

			// Удаление труб за экраном
			if (bottomTube.x + TUBE_WIDTH < 0) {
				bottomTubes.removeIndex(i);
				tubeScored.removeIndex(i);
				i--;
			}
		}
	}

	private void drawPlaying() {
		// Выбор анимации: вверх - fly (1, 2), вниз - fall (1, 0)
		TextureRegion currentFrame;
		if (birdVelocity > 0) {
			currentFrame = flyAnimation.getKeyFrame(birdAnimationTime, true);
		} else {
			currentFrame = fallAnimation.getKeyFrame(birdAnimationTime, true);
		}

		batch.draw(currentFrame, bird.x, bird.y, bird.width, bird.height);

		for (Rectangle bottomTube : bottomTubes) {
			// Нижняя
			batch.draw(tubeTexture, bottomTube.x, bottomTube.y, bottomTube.width, bottomTube.height);
			// Верхняя
			float topY = bottomTube.height + TUBE_GAP;
			float topHeight = SCR_HEIGHT - topY;
			batch.draw(tubeFlippedTexture, bottomTube.x, topY, bottomTube.width, topHeight);
		}

		font.draw(batch, "Score: " + score, 20, SCR_HEIGHT - 20);
	}

	private void resetGame() {
		bird.setPosition(250, SCR_HEIGHT / 2f);
		birdVelocity = 0;
		score = 0;
		birdAnimationTime = 0;
		bottomTubes.clear();
		tubeScored.clear();
		tubeSpawnTimer = 0;
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height, true);
	}

	@Override
	public void dispose() {
		batch.dispose();
		font.dispose();
		for (Texture tex : birdTextures) tex.dispose();
		tubeTexture.dispose();
		tubeFlippedTexture.dispose();
		gameBgTexture.dispose();
		restartBgTexture.dispose();
		buttonTexture.dispose();
	}
}