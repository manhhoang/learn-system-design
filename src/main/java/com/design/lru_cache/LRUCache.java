package com.design.lru_cache;

import java.util.HashMap;

/**
 * Implement Least Recently Used (LRU) Cache.
 */
public class LRUCache {

    class Node {
        int key;
        int value;
        Node pre;
        Node next;

        public Node(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    int capacity;
    HashMap<Integer, Node> map = new HashMap<>();
    Node head = null;
    Node end = null;

    public LRUCache(int capacity) {
        this.capacity = capacity;
    }

    public int get(int key) {
        if (map.containsKey(key)) {
            Node n = map.get(key);
            remove(n);
            setHead(n);
            return n.value;
        }
        return -1;
    }

    public void set(int key, int value) {
        if (map.containsKey(key)) {
            Node old = map.get(key);
            old.value = value;
            remove(old);
            setHead(old);
        } else {
            Node created = new Node(key, value);
            if (map.size() >= capacity) {
                map.remove(end.key);
                remove(end);
                setHead(created);
            } else {
                setHead(created);
            }
            map.put(key, created);
        }
    }

    private void remove(Node n) {
        if (n.pre != null) {
            n.pre.next = n.next;
        } else {
            head = n.next;
        }

        if (n.next != null) {
            n.next.pre = n.pre;
        } else {
            end = n.pre;
        }
    }

    private void setHead(Node n) {
        n.next = head;
        n.pre = null;
        if (head != null)
            head.pre = n;
        head = n;
        if (end == null)
            end = head;
    }

    public static void main(String[] args) {
        LRUCache lruCache = new LRUCache(3);
        lruCache.set(1, 1000);
        System.out.println(lruCache.get(1));
        lruCache.set(2, 2000);
        System.out.println(lruCache.get(2));
        lruCache.set(3, 3000);
        System.out.println(lruCache.get(3));
        lruCache.set(4, 4000);
        System.out.println(lruCache.get(4));

        System.out.println(lruCache.get(1));
    }
}