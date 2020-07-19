package dev.robocode.tankroyale.botapi;

import dev.robocode.tankroyale.botapi.events.TickEvent;
import dev.robocode.tankroyale.schema.BotIntent;
import dev.robocode.tankroyale.schema.BotReady;

import static java.lang.Math.abs;

final class BotInternals {

  private final double ABS_DECELERATION = Math.abs(IBot.DECELERATION);

  private final Bot bot;
  private final BotEvents botEvents;

  double maxSpeed = IBot.MAX_SPEED;
  double maxTurnRate = IBot.MAX_TURN_RATE;
  double maxGunTurnRate = IBot.MAX_GUN_TURN_RATE;
  double maxRadarTurnRate = IBot.MAX_RADAR_TURN_RATE;

  double distanceRemaining;
  double turnRemaining;
  double gunTurnRemaining;
  double radarTurnRemaining;

  private boolean isCollidingWithBot;
  private boolean isOverDriving;

  private TickEvent currentTick;

  private Thread thread;
  private final Object nextTurn = new Object();
  volatile boolean isRunning;

  boolean isStopped;
  BotIntent savedBotIntent;
  double savedDistanceRemaining;
  double savedTurnRemaining;
  double savedGunTurnRemaining;
  double savedRadarTurnRemaining;

  int awaitTurn = -1;

  BotInternals(Bot bot, BotEvents botEvents) {
    this.bot = bot;
    this.botEvents = botEvents;

    botEvents.onDisconnected.subscribe(e -> stopThread());
    botEvents.onGameEnded.subscribe(e -> stopThread());
    botEvents.onHitBot.subscribe(e -> onHitBot(e.isRammed()));
    botEvents.onHitWall.subscribe(e -> onHitWall());
    botEvents.onTick.subscribe(
        e -> {
          currentTick = e;
          onTick();
        });
    botEvents.onBotDeath.subscribe(
        e -> {
          if (e.getVictimId() == bot.getMyId()) {
            stopThread();
          }
        });
  }

  private void onTick() {
    processTurn();
  }

  private void onHitBot(boolean isRammed) {
    if (isRammed) {
      distanceRemaining = 0;
    }
    isCollidingWithBot = true;
  }

  private void onHitWall() {
    distanceRemaining = 0;
  }

  private void processTurn() {
    // No movement is possible, when the bot has become disabled
    if (bot.isDisabled()) {
      distanceRemaining = 0;
      turnRemaining = 0;
    }
    updateHeadings();
    updateMovement();
    isCollidingWithBot = false;

    // If this is the first turn -> Call the run method on the Bot class
    if (currentTick.getTurnNumber() == 1) {
      if (isRunning) {
        stopThread();
      }
      startThread();
    }

    // Unblock waiting methods
    synchronized (nextTurn) {
      // Let's go ;-)
      bot.go();

      // Unblock waiting methods waiting for the next turn
      nextTurn.notifyAll();
    }
  }

  private void startThread() {
    thread = new Thread(bot::run);
    thread.start();
    isRunning = true;
  }

  private void stopThread() {
    if (thread != null) {
      isRunning = false;
      thread.interrupt();
      try {
        thread.join();
      } catch (InterruptedException ignored) {
      }
      thread = null;
    }
  }

  /** Updates the bot heading, gun heading, and radar heading. */
  private void updateHeadings() {
    if (!isCollidingWithBot) {
      updateTurnRemaining();
    }
    updateGunTurnRemaining();
    updateRadarTurnRemaining();
  }

  private void updateTurnRemaining() {
    final double absTurnRate = abs(bot.getTurnRate());

    double turnRate = Math.min(absTurnRate, bot.calcMaxTurnRate(bot.getSpeed()));
    if (bot.getTurnRemaining() < 0) {
      turnRate *= -1;
    }
    if (abs(bot.getTurnRemaining()) < absTurnRate) {
      if (bot.isAdjustGunForBodyTurn()) {
        gunTurnRemaining -= bot.getTurnRemaining();
      }
      turnRemaining = 0;
    } else {
      if (bot.isAdjustGunForBodyTurn()) {
        gunTurnRemaining -= turnRate;
      }
      turnRemaining -= turnRate;
    }
    if (turnRemaining > 0) {
      bot.setTurnRate(Math.min(maxTurnRate, turnRemaining));
    } else {
      bot.setTurnRate(Math.max(-maxTurnRate, turnRemaining));
    }
  }

  private void updateGunTurnRemaining() {
    final double absGunTurnRate = abs(bot.getGunTurnRate());

    if (abs(bot.getGunTurnRemaining()) < absGunTurnRate) {
      if (bot.isAdjustRadarForGunTurn()) {
        radarTurnRemaining -= bot.getGunTurnRemaining();
      }
      gunTurnRemaining = 0;
    } else {
      if (bot.isAdjustRadarForGunTurn()) {
        radarTurnRemaining -= bot.getGunTurnRate();
      }
      gunTurnRemaining -= bot.getGunTurnRate();
    }
    if (gunTurnRemaining > 0) {
      bot.setGunTurnRate(Math.min(maxGunTurnRate, gunTurnRemaining));
    } else {
      bot.setGunTurnRate(Math.max(-maxGunTurnRate, gunTurnRemaining));
    }
  }

  private void updateRadarTurnRemaining() {
    final double absRadarTurnRate = abs(bot.getRadarTurnRate());

    if (abs(bot.getRadarTurnRemaining()) < absRadarTurnRate) {
      radarTurnRemaining = 0;
    } else {
      radarTurnRemaining -= bot.getRadarTurnRate();
    }
    if (radarTurnRemaining > 0) {
      bot.setRadarTurnRate(Math.min(maxRadarTurnRate, radarTurnRemaining));
    } else {
      bot.setRadarTurnRate(Math.max(-maxRadarTurnRate, radarTurnRemaining));
    }
  }

  /** Updates the movement. */
  // This is Nat Pavasants method described here:
  // http://robowiki.net/wiki/User:Positive/Optimal_Velocity#Nat.27s_updateMovement
  private void updateMovement() {
    double distance = distanceRemaining;
    if (Double.isNaN(distance)) {
      distance = 0;
    }

    double speed = getNewSpeed(bot.getSpeed(), distance);
    bot.setTargetSpeed(speed);

    // If we are over-driving our distance and we are now at velocity=0 then we stopped
    if (isNearZero(speed) && isOverDriving) {
      distanceRemaining = 0;
      distance = 0;
      isOverDriving = false;
    }

    // If we are moving normally and the breaking distance is more than remaining distance, enable
    // the overdrive flag
    if (Math.signum(distance * speed) != -1) {
      isOverDriving = getDistanceTraveledUntilStop(speed) > Math.abs(distance);
    }

    distanceRemaining = distance - speed;
  }

  /**
   * Returns the new speed based on the current speed and distance to move.
   *
   * @param speed is the current speed
   * @param distance is the distance to move
   * @return The new speed
   */
  // Credits for this algorithm goes to Patrick Cupka (aka Voidious),
  // Julian Kent (aka Skilgannon), and Positive:
  // https://robowiki.net/wiki/User:Voidious/Optimal_Velocity#Hijack_2
  private double getNewSpeed(double speed, double distance) {

    if (distance < 0) {
      // If the distance is negative, then change it to be positive and change the sign of the
      // input velocity and the result
      return -getNewSpeed(-speed, -distance);
    }

    final double targetSpeed;
    if (distance == Double.POSITIVE_INFINITY) {
      targetSpeed = maxSpeed;
    } else {
      targetSpeed = Math.min(getMaxSpeed(distance), maxSpeed);
    }

    if (speed >= 0) {
      return Math.max(speed - ABS_DECELERATION, Math.min(targetSpeed, speed + IBot.ACCELERATION));
    } // else
    return Math.max(
        speed - IBot.ACCELERATION, Math.min(targetSpeed, speed + getMaxDeceleration(-speed)));
  }

  private double getMaxSpeed(double distance) {
    double decelTime =
        Math.max(
            1,
            Math.ceil( // sum of 0... decelTime, solving for decelTime using quadratic formula
                (Math.sqrt((4 * 2 / ABS_DECELERATION) * distance + 1) - 1) / 2));

    if (decelTime == Double.POSITIVE_INFINITY) {
      return IBot.MAX_SPEED;
    }

    double decelDist =
        (decelTime / 2)
            * (decelTime - 1) // sum of 0..(decelTime-1)
            * ABS_DECELERATION;

    return ((decelTime - 1) * ABS_DECELERATION) + ((distance - decelDist) / decelTime);
  }

  private double getMaxDeceleration(double speed) {
    double decelTime = speed / ABS_DECELERATION;
    double accelTime = (1 - decelTime);

    return Math.min(1, decelTime) * ABS_DECELERATION + Math.max(0, accelTime) * IBot.ACCELERATION;
  }

  private double getDistanceTraveledUntilStop(double speed) {
    speed = Math.abs(speed);
    double distance = 0;
    while (speed > 0) {
      distance += (speed = getNewSpeed(speed, 0));
    }
    return distance;
  }

  void stop() {
    if (!isStopped) {
      final BotIntent botIntent = bot.__baseBotInternals.botIntent;
      savedBotIntent = botIntent;

      BotIntent newIntent = new BotIntent();

      newIntent.set$type(BotReady.$type.BOT_INTENT); // must be set!
      newIntent.setTargetSpeed(0d);
      newIntent.setTurnRate(0d);
      newIntent.setGunTurnRate(0d);
      newIntent.setRadarTurnRate(0d);
      newIntent.setFirepower(0d);
      newIntent.setBodyColor(botIntent.getBodyColor());
      newIntent.setTurretColor(botIntent.getTurretColor());
      newIntent.setGunColor(botIntent.getGunColor());
      newIntent.setRadarColor(botIntent.getRadarColor());
      newIntent.setBulletColor(botIntent.getBulletColor());
      newIntent.setScanColor(botIntent.getScanColor());
      newIntent.setTracksColor(botIntent.getTracksColor());

      bot.__baseBotInternals.botIntent = newIntent;

      savedDistanceRemaining = distanceRemaining;
      savedTurnRemaining = turnRemaining;
      savedGunTurnRemaining = gunTurnRemaining;
      savedRadarTurnRemaining = radarTurnRemaining;

      System.out.println("saved BotIntent: " + savedBotIntent);

      isStopped = true;
    }
  }

  void resume() {
    if (isStopped) {
      System.out.println("restored BotIntent: " + savedBotIntent);

      bot.__baseBotInternals.botIntent = savedBotIntent;
      distanceRemaining = savedDistanceRemaining;
      turnRemaining = savedTurnRemaining;
      gunTurnRemaining = savedGunTurnRemaining;
      radarTurnRemaining = savedRadarTurnRemaining;

      isStopped = false;
    }
  }

  private boolean isNearZero(double value) {
    return (Math.abs(value) < .00001);
  }

  void awaitMovementComplete() {
    System.out.println("awaitMovementComplete: " + currentTick.getTurnNumber());
    await(() -> distanceRemaining == 0);
  }

  void awaitTurnComplete() {
    System.out.println("awaitTurnComplete: " + currentTick.getTurnNumber());
    await(() -> turnRemaining == 0);
  }

  void awaitGunTurnComplete() {
    System.out.println("awaitGunTurnComplete: " + currentTick.getTurnNumber());
    await(() -> gunTurnRemaining == 0);
  }

  void awaitRadarTurnComplete() {
    System.out.println("awaitRadarTurnComplete: " + currentTick.getTurnNumber());
    await(() -> radarTurnRemaining == 0);
  }

  void awaitGunFired() {
    System.out.println("awaitGunFired: " + currentTick.getTurnNumber());
    await(() -> bot.getGunHeat() > 0);
  }

  void awaitNextTurn() {
    awaitTurn = currentTick.getTurnNumber();
    await(() -> currentTick.getTurnNumber() > awaitTurn);
  }

  private void await(ICondition condition) {
    synchronized (nextTurn) {
      // Loop while bot is running and condition has not been met
      while (isRunning && !condition.test()) {
        try {
          nextTurn.wait();
          botEvents.dispatchEvents(currentTick);
        } catch (InterruptedException e) {
          isRunning = false;
        }
      }
    }
  }

  private interface ICondition {
    boolean test();
  }
}
