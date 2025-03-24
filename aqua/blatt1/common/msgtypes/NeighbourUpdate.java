package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.Direction;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NeighbourUpdate implements Serializable {
    private final Direction direction;
    private final InetSocketAddress new_neighbour;

    public NeighbourUpdate(Direction direction, InetSocketAddress new_neighbour) {
        this.direction = direction;
        this.new_neighbour = new_neighbour;
    }

    public Direction getDirection() {return direction;}

    public InetSocketAddress getNewNeighbour() {
        return new_neighbour;
    }
}
