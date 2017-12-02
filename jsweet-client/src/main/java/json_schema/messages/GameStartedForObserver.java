package json_schema.messages;

import static def.jquery.Globals.$;

import java.util.HashSet;
import java.util.Set;

import def.js.Array;
import json_schema.GameSetup;
import json_schema.Participant;

public class GameStartedForObserver extends Message {

	public static final String TYPE = "gameStartedForObserver";

	public GameStartedForObserver() {
		super(TYPE);
	}

	public GameStartedForObserver(String type) {
		super(type);
	}

	public GameSetup getGameSetup() {
		return (GameSetup) $.extend(false, new GameSetup(), $get("gameSetup"));
	}

	public Set<Participant> getParticipants() {
		@SuppressWarnings("unchecked")
		Array<Participant> array = (Array<Participant>) $get("participants");

		Set<Participant> set = new HashSet<>();
		for (Participant obj : array) {
			Participant participant = (Participant) $.extend(false, new Participant(), obj);
			set.add(participant);
		}
		return set;
	}

	public static GameStartedForObserver map(Object obj) {
		return (GameStartedForObserver) $.extend(false, new GameStartedForObserver(), obj);
	}
}