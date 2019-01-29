package net.robocode2.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import net.robocode2.schema.comm.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import net.robocode2.schema.events.Event;
import net.robocode2.schema.events.ScannedBotEvent;
import net.robocode2.schema.events.GameStartedEventForBot;
import net.robocode2.schema.events.TickEventForBot;
import net.robocode2.schema.types.Point;
import net.robocode2.util.MathUtil;

public class BotClient1 extends WebSocketClient {

	final Gson gson;
	{
		RuntimeTypeAdapterFactory<Event> typeFactory = RuntimeTypeAdapterFactory.of(Event.class)
				// .registerSubtype(BotDeathEvent.class, "botDeathEvent")
				// .registerSubtype(BotHitBotEvent.class, "botHitBotEvent")
				// .registerSubtype(BotHitWallEvent.class, "botHitWallEvent")
				// .registerSubtype(BulletFiredEvent.class, "bulletFiredEvent")
				// .registerSubtype(BulletHitBotEvent.class, "bulletHitBotEvent")
				// .registerSubtype(BulletHitBulletEvent.class, "bulletHitBulletEvent")
				// .registerSubtype(BulletMissedEvent.class, "bulletMissedEvent")
				.registerSubtype(ScannedBotEvent.class, "scannedBotEvent")
		// .registerSubtype(SkippedTurnEvent.class, "skippedTurnEvent")
		;

		gson = new GsonBuilder().registerTypeAdapterFactory(typeFactory).create();
	}

	static final String TYPE = "type";
	static final String CLIENT_KEY = "clientKey";

	String clientKey;

	int turn;
	double targetSpeed = 10;

	Point targetPos;

	public BotClient1(URI serverUri, Draft draft) {
		super(serverUri, draft);
	}

	public BotClient1(URI serverURI) {
		super(serverURI);
	}

	@Override
	public void onOpen(org.java_websocket.handshake.ServerHandshake serverHandshake) {
		System.out.println("onOpen()");
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println("onClose(), code: " + code + ", reason: " + reason);
	}

	@Override
	public void onMessage(String message) {
		System.out.println("onMessage(): " + message);

		JsonObject jsonMessage = gson.fromJson(message, JsonObject.class);

		JsonElement jsonType = jsonMessage.get(TYPE);
		if (jsonType != null) {
			String type = jsonType.getAsString();

			if (ServerHandshake.Type.SERVER_HANDSHAKE.toString().equalsIgnoreCase(type)) {
				clientKey = jsonMessage.get(CLIENT_KEY).getAsString();

				// Send bot handshake
				BotHandshake handshake = new BotHandshake();
				handshake.setType(BotHandshake.Type.BOT_HANDSHAKE);
				handshake.setClientKey(clientKey);
				handshake.setName("Bot name");
				handshake.setVersion("0.1");
				handshake.setAuthor("Author name");
				handshake.setCountryCode("DK");
				handshake.setGameTypes(Arrays.asList("melee", "1v1"));
				handshake.setProgrammingLanguage("Java");

				String msg = gson.toJson(handshake);
				send(msg);

			} else if (GameStartedEventForBot.Type.GAME_STARTED_EVENT_FOR_BOT.toString().equalsIgnoreCase(type)) {
				// Send ready signal
				BotReady ready = new BotReady();
				ready.setType(BotReady.Type.BOT_READY);
				ready.setClientKey(clientKey);

				String msg = gson.toJson(ready);
				send(msg);

			} else if (TickEventForBot.Type.TICK_EVENT_FOR_BOT.toString().equalsIgnoreCase(type)) {
				TickEventForBot tick = gson.fromJson(message, TickEventForBot.class);

				Point botPos = tick.getBotState().getPosition();

				// Prepare intent
				BotIntent intent = new BotIntent();
				intent.setType(BotIntent.Type.BOT_INTENT);
				intent.setClientKey(clientKey);

				for (Event event : tick.getEvents()) {
					if (event instanceof ScannedBotEvent) {
						ScannedBotEvent scanEvent = (ScannedBotEvent) event;
						targetPos = scanEvent.getPosition();
					}
				}

				if (++turn % 25 == 0) {
					targetSpeed *= -1;
					intent.setTargetSpeed(targetSpeed);
				}

				intent.setBulletPower(Math.random() * 2.9 + 0.1);
				intent.setRadarTurnRate(45.0);

				if (targetPos != null) {
					double dx = targetPos.getX() - botPos.getX();
					double dy = targetPos.getY() - botPos.getY();

					double angle = Math.toDegrees(Math.atan2(dy, dx));

					double gunTurnRate = MathUtil.normalRelativeDegrees(angle - tick.getBotState().getGunDirection());

					intent.setGunTurnRate(gunTurnRate);
				}

				// Send intent
				String msg = gson.toJson(intent);
				send(msg);
			}
		}

	}

	@Override
	public void onError(Exception ex) {
		System.err.println("onError():" + ex);
	}

	public static void main(String[] args) throws URISyntaxException {
		WebSocketClient client = new BotClient1(new URI("ws://localhost:50000"));
		client.connect();
	}

	@Override
	public void send(String message) {
		System.out.println("Sending: " + message);

		super.send(message);
	}
}