package org.example.design.states;

/**
 * They can also trigger state transitions in the context.
 */
public class ReadyState extends State {

    public ReadyState(Player player) {
        super(player);
    }

    @Override
    public String onLock() {
        player.changeState(new LockedState(player));
        return "Locked...";
    }

    @Override
    public String onPlay() {
        String action = player.startPlayback();
        player.changeState(new PlayingState(player));
        return action;
    }

    @Override
    public String onNext() {
        player.changeState(new PlayingState(player));
        return player.nextTrack();
    }

    @Override
    public String onPrevious() {
        player.changeState(new PlayingState(player));
        return player.previousTrack();
    }
}