package ds.lds;
import java.util.*;

public class ConvexHull {
    // Used in: Computer graphics, collision detection, pattern recognition
    // Time: O(n log n)

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) {
        Point[] points = {
                new Point(0, 3), new Point(1, 1), new Point(2, 2),
                new Point(4, 4), new Point(0, 0), new Point(1, 2),
                new Point(3, 1), new Point(3, 3)
        };

        System.out.println("Input points:");
        for (Point p : points) {
            System.out.println("(" + p.x + ", " + p.y + ")");
        }

        ArrayList<Point> hull = convexHull(points);

        System.out.println("\nConvex Hull:");
        for (Point p : hull) {
            System.out.println("(" + p.x + ", " + p.y + ")");
        }
    }

    static ArrayList<Point> convexHull(Point[] points) {
        int n = points.length;
        if (n < 3) return new ArrayList<>();

        // Find bottommost point (or leftmost in case of tie)
        int minY = 0;
        for (int i = 1; i < n; i++) {
            if (points[i].y < points[minY].y ||
                    (points[i].y == points[minY].y && points[i].x < points[minY].x)) {
                minY = i;
            }
        }

        // Swap with first position
        Point temp = points[0];
        points[0] = points[minY];
        points[minY] = temp;

        final Point pivot = points[0];

        // Sort by polar angle
        Arrays.sort(points, 1, n, (p1, p2) -> {
            int orient = orientation(pivot, p1, p2);
            if (orient == 0) {
                return distance(pivot, p1) - distance(pivot, p2);
            }
            return (orient == 2) ? -1 : 1;
        });

        // Create hull
        Stack<Point> stack = new Stack<>();
        stack.push(points[0]);
        stack.push(points[1]);

        for (int i = 2; i < n; i++) {
            while (stack.size() > 1) {
                Point top = stack.pop();
                Point secondTop = stack.peek();

                if (orientation(secondTop, top, points[i]) != 2) {
                    stack.push(top);
                    break;
                }
            }
            stack.push(points[i]);
        }

        return new ArrayList<>(stack);
    }

    static int orientation(Point p, Point q, Point r) {
        int val = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y);
        if (val == 0) return 0;  // Collinear
        return (val > 0) ? 1 : 2;  // Clockwise or Counterclockwise
    }

    static int distance(Point p1, Point p2) {
        return (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y);
    }
}