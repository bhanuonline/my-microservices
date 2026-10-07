package dailytest.ds;
class Node{
    int value;
    Node left, right;
    public Node (int value){
        this.value=value;
        this.left=null;
        this.right=null;
    }
}

class BST{
    Node root;
    public boolean serach(Node current,int target){
        if(current==null){
            return false;
        }
        if(current.value==target){
            return true;
        }
        if(current.value<target){
            serach(current.left, target);
        }
        return serach(current.right, target);
    }

    public Node insert(Node current, int value){
        if(current==null){
            return new Node(value);
        }
        if(current.value < value){
            current.right =  insert(current.right,value);
        } else if (root.value>value) {
            current.left =  insert(current.left,value);
        }
        return current;
    }

    // ─── INORDER PRINT (to verify BST) ───────────────
    public void inorder(Node root) {
        if (root == null) return;
        inorder(root.left);
        System.out.print(root.value + " ");
        inorder(root.right);
    }
    // Preorder: Root → Left → Right
    void preorder(Node root) {
        if (root == null) return;
        System.out.print(root.value + " ");
        preorder(root.left);
        preorder(root.right);
    }

    // Postorder: Left → Right → Root
    void postorder(Node root) {
        if (root == null) return;
        postorder(root.left);
        postorder(root.right);
        System.out.print(root.value + " ");
    }
}
public class BSTTest {
    public static void main(String[] args) {

        BST tree=new BST();
        // Insert values
        int[] values = {40, 20, 60, 10, 30, 50, 70};
        for (int v : values)
            tree.root = tree.insert(tree.root, v);

        // Print tree (inorder = sorted order)
        System.out.print("Inorder: ");
        tree.inorder(tree.root);
        System.out.println(tree.serach(tree.root,10));;

    }
}
