package maxver90.tree;

import maxver90.tree.entity.Tree;

import javax.persistence.*;
import java.util.List;
import java.util.Scanner;

public class TreeApplication {

    private static final EntityManagerFactory FACTORY =
            Persistence.createEntityManagerFactory("main-connection");

    private static final Scanner IN = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("""
                Добавить категорию [1]
                Переместить категорию [2]
                Удалить категорию [3]
                Выберите дейсвтие: \
                """);
        String actionNum = IN.nextLine();
        switch (actionNum) {
            case "1" -> create();
            case "2" -> move();
            case "3" -> delete();
            default -> System.out.println("Такого действия не существует");
        }
    }

    private static void create() {
        EntityManager manager = FACTORY.createEntityManager();
        try {
            manager.getTransaction().begin();
            printTree();
            System.out.println("Введите ID категории, в которую хотите добавить (0 - для создания новой категории)");
            long parentId = Long.parseLong(IN.nextLine());

            Tree newTree = new Tree();
            System.out.print("Введите название: ");
            String name = IN.nextLine();
            newTree.setName(name);

            if (parentId > 0) {
                Tree parent = manager.find(Tree.class, parentId);

                Query updateLeftKeysQuery = manager.createQuery(
                        "update Tree t set t.leftKey = t.leftKey + 2 where t.leftKey > ?1"
                );
                updateLeftKeysQuery.setParameter(1, parent.getRightKey());
                updateLeftKeysQuery.executeUpdate();
                Query updateRightKeysQuery = manager.createQuery(
                        "update Tree t set t.rightKey = t.rightKey + 2 where t.rightKey >= ?1"
                );
                updateRightKeysQuery.setParameter(1, parent.getRightKey());
                updateRightKeysQuery.executeUpdate();

                newTree.setLeftKey(parent.getRightKey());
                newTree.setRightKey(parent.getRightKey() + 1);
                newTree.setLevel(parent.getLevel() + 1);
            } else if (parentId == 0) {
                TypedQuery<Integer> maxRightKeyQuery = manager.createQuery(
                        "select max(t.rightKey) from Tree t", Integer.class
                );
                newTree.setLeftKey(maxRightKeyQuery.getSingleResult());
                newTree.setRightKey(maxRightKeyQuery.getSingleResult() + 1);
                newTree.setLevel(1);
            }
            manager.persist(newTree);
            manager.getTransaction().commit();
        } catch (Exception e) {
            manager.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            manager.close();
        }
    }

    private static void move() {
        EntityManager manager = FACTORY.createEntityManager();
        try {
            manager.getTransaction().begin();
            printTree();
            System.out.println("Введите ID категории, которую нужно переместить: ");
            long moveTreeId = Long.parseLong(IN.nextLine());

            Tree moveTree = manager.find(Tree.class, moveTreeId);
            Query toNegativeQuery = manager.createQuery(
                    "update Tree t set t.leftKey = 0 - t.leftKey," +
                            "t.rightKey = 0 - t.rightKey where t.leftKey >= ?1 and t.rightKey <= ?2"
            );
            toNegativeQuery.setParameter(1, moveTree.getLeftKey());
            toNegativeQuery.setParameter(2, moveTree.getRightKey());
            toNegativeQuery.executeUpdate();

            int moveTreeSize = moveTree.getRightKey() - moveTree.getLeftKey() + 1;

            Query removeSpaceLeftKeyQuery = manager.createQuery(
                    "update Tree t set t.leftKey = t.leftKey - ?1 where t.leftKey > ?2"
            );
            removeSpaceLeftKeyQuery.setParameter(1, moveTreeSize);
            removeSpaceLeftKeyQuery.setParameter(2, moveTree.getRightKey());
            removeSpaceLeftKeyQuery.executeUpdate();

            Query removeSpaceRightKeyQuery = manager.createQuery(
                    "update Tree t set t.rightKey = t.rightKey - ?1 where t.rightKey > ?2"
            );
            removeSpaceRightKeyQuery.setParameter(1, moveTreeSize);
            removeSpaceRightKeyQuery.setParameter(2, moveTree.getRightKey());
            removeSpaceRightKeyQuery.executeUpdate();

            System.out.println("Введите ID, куда перемещать (0, если нужно создать новую ветку): ");
            long targetId = Long.parseLong(IN.nextLine());
            if (targetId == 0) {
                TypedQuery<Integer> maxKeyQuery = manager.createQuery(
                        "select max(t.rightKey) from Tree t", Integer.class
                );
                Query toTargetQuery = manager.createQuery(
                        "update Tree t set t.leftKey = 0 - t.leftKey - ?1 + ?2 + 1," +
                                " t.rightKey = 0 - t.rightKey - ?1 + ?2 + 1," +
                                " t.level = t.level - ?3 + 1 where t.leftKey < 0"
                );
                toTargetQuery.setParameter(1, moveTree.getLeftKey());
                toTargetQuery.setParameter(2, maxKeyQuery.getSingleResult());
                toTargetQuery.setParameter(3, moveTree.getLevel());
                toTargetQuery.executeUpdate();
            } else if (targetId > 0) {
                Tree target = manager.find(Tree.class, targetId);
                Query allocLKSpaceQuery = manager.createQuery(
                        "update Tree t set t.leftKey = t.leftKey + ?1 where t.leftKey > ?2"
                );
                allocLKSpaceQuery.setParameter(1, moveTreeSize);
                allocLKSpaceQuery.setParameter(2, target.getRightKey());
                allocLKSpaceQuery.executeUpdate();

                Query allocRKSpaceQuery = manager.createQuery(
                        "update Tree t set t.rightKey = t.rightKey + ?1 where t.rightKey >= ?2"
                );
                allocRKSpaceQuery.setParameter(1, moveTreeSize);
                allocRKSpaceQuery.setParameter(2, target.getRightKey());
                allocRKSpaceQuery.executeUpdate();

                manager.refresh(target);

                Query toTargetQuery = manager.createQuery(
                        "update Tree t set t.leftKey = 0 - t.leftKey + ?1," +
                                " t.rightKey = 0 - t.rightKey + ?1," +
                                " t.level = t.level - ?2 + 1 where t.leftKey < 0"
                );
                toTargetQuery.setParameter(1, target.getRightKey() - moveTree.getRightKey() - 1);
                toTargetQuery.setParameter(2, moveTree.getLevel() - target.getLevel());
                toTargetQuery.executeUpdate();
            }
            manager.getTransaction().commit();
        } catch (Exception e) {
            manager.getTransaction().rollback();
        } finally {
            manager.close();
        }
    }

    private static void delete() {
        EntityManager manager = FACTORY.createEntityManager();
        try {
            manager.getTransaction().begin();
            printTree();
            System.out.println("Введите ID категории, которую хотите удалить: ");
            long parentId = Long.parseLong(IN.nextLine());

            Tree parent = manager.find(Tree.class, parentId);

            Query deleteCategoryQuery = manager.createQuery(
                    "delete from Tree t where t.leftKey >= ?1 and t.rightKey <= ?2");
            deleteCategoryQuery.setParameter(1, parent.getLeftKey());
            deleteCategoryQuery.setParameter(2, parent.getRightKey());
            deleteCategoryQuery.executeUpdate();

            int parentSize = parent.getRightKey() - parent.getLeftKey() + 1;

            Query updateLeftKeysQuery = manager.createQuery(
                    "update Tree set leftKey = leftKey - ?1  where leftKey >= ?2"
            );
            updateLeftKeysQuery.setParameter(1, parentSize);
            updateLeftKeysQuery.setParameter(2, parent.getRightKey());
            updateLeftKeysQuery.executeUpdate();

            Query updateRightKeysQuery = manager.createQuery(
                    "update Tree t set t.rightKey = t.rightKey - ?1 where t.rightKey >= ?2"
            );
            updateRightKeysQuery.setParameter(1, parentSize);
            updateRightKeysQuery.setParameter(2, parent.getRightKey());
            updateRightKeysQuery.executeUpdate();

            manager.getTransaction().commit();
        } catch (Exception e) {
            manager.getTransaction().rollback();
        } finally {
            manager.close();
        }
    }

    private static void printTree() {
        EntityManager manager = FACTORY.createEntityManager();
        List<Tree> trees = manager
                .createQuery("select t from Tree t order by t.leftKey", Tree.class)
                .getResultList();
        for (Tree tree : trees) {
            String Dashes = "- ".repeat(tree.getLevel());
            System.out.println(Dashes + tree.getName() + "[" + tree.getId() + "]");
        }
        manager.close();
    }
}