package nio.clear.client.single;

import java.util.LinkedList;

public class UnfairQueue extends LinkedList<Msg> {

    @Override
    public synchronized Msg peek() { return super.peek();
    }

    @Override
    public synchronized Msg poll() {
        return super.poll();
    }

    public synchronized void enqueue(Msg node) {
        super.add(node);
    }

    public synchronized void enqueue(Msg node, int index) {
        super.add(index, node);
    }
}
