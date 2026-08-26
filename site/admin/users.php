<?php
declare(strict_types=1);

/**
 * Список позывных для администратора.
 *
 * Публично такого списка нет и не будет — в этом весь смысл проекта. Но
 * хозяину сайта нужно уметь заблокировать позывной и дотянуться до его ленты,
 * иначе модерировать нечем.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';
efir_require_installed();
require_once __DIR__ . '/_layout.php';
admin_require();

$notice = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    $id = (int)($_POST['id'] ?? 0);
    $action = (string)($_POST['action'] ?? '');

    if ($id > 0) {
        switch ($action) {
            case 'block':
                db_exec('UPDATE users SET is_blocked = 1 WHERE id = ?', [$id]);
                db_exec('UPDATE posts SET is_hidden = 1 WHERE user_id = ?', [$id]);
                $notice = 'Позывной заблокирован, его передачи сняты с эфира.';
                break;
            case 'unblock':
                db_exec('UPDATE users SET is_blocked = 0 WHERE id = ?', [$id]);
                $notice = 'Позывной разблокирован. Передачи возвращайте вручную.';
                break;
        }
    }
}

$perPage = 50;
$page = max(1, (int)($_GET['page'] ?? 1));
$offset = ($page - 1) * $perPage;

$totalRow = db_one('SELECT COUNT(*) AS c FROM users');
$total = (int)($totalRow['c'] ?? 0);
$pages = max(1, (int)ceil($total / $perPage));

$rows = db_all('SELECT * FROM users ORDER BY id DESC LIMIT ' . $perPage . ' OFFSET ' . $offset);

admin_header('Позывные', true);
?>

<?php if ($notice !== null): ?>
    <div class="notice notice-ok"><?= e($notice) ?></div>
<?php endif; ?>

<div class="notice">
    <p class="muted">
        Учётная запись — это позывной и кодовое слово, больше о человеке ничего
        не известно. Кодовое слово сюда не попадает даже в момент регистрации:
        приложение присылает только его хеш. Восстановить забытое кодовое слово
        невозможно — ни вам, ни владельцу.
    </p>
</div>

<p class="muted">
    Всего <?= $total ?> <?= plural($total, 'позывной', 'позывных', 'позывных') ?>,
    страница <?= $page ?> из <?= $pages ?>.
</p>

<table class="rows">
    <tr>
        <th>позывной</th>
        <th>лента</th>
        <th>передач</th>
        <th>заведён</th>
        <th>был в эфире</th>
        <th>действия</th>
    </tr>
    <?php foreach ($rows as $row): ?>
        <tr class="<?= (int)$row['is_blocked'] === 1 ? 'hidden-row' : '' ?>">
            <td><?= e((string)$row['handle']) ?></td>
            <td>
                <a href="/u/<?= e((string)$row['profile_code']) ?>" target="_blank">
                    <?= e((string)$row['profile_code']) ?>
                </a>
            </td>
            <td><?= (int)$row['posts_count'] ?></td>
            <td><?= e(date('d.m.y', strtotime((string)$row['created_at']))) ?></td>
            <td>
                <?= $row['last_seen_at'] !== null
                    ? e(date('d.m.y H:i', strtotime((string)$row['last_seen_at'])))
                    : '—' ?>
            </td>
            <td>
                <form method="post" class="inline-form">
                    <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
                    <input type="hidden" name="id" value="<?= (int)$row['id'] ?>">
                    <?php if ((int)$row['is_blocked'] === 1): ?>
                        <button class="btn btn-ghost" name="action" value="unblock" type="submit">разблокировать</button>
                    <?php else: ?>
                        <button class="btn btn-danger" name="action" value="block" type="submit">заблокировать</button>
                    <?php endif; ?>
                </form>
            </td>
        </tr>
    <?php endforeach; ?>
    <?php if (!$rows): ?>
        <tr><td colspan="6" class="muted">Пока никто не зарегистрировался.</td></tr>
    <?php endif; ?>
</table>

<?php if ($pages > 1): ?>
    <p style="margin-top:18px">
        <?php for ($i = 1; $i <= $pages; $i++): ?>
            <?php if ($i === $page): ?>
                <strong><?= $i ?></strong>
            <?php else: ?>
                <a href="?page=<?= $i ?>"><?= $i ?></a>
            <?php endif; ?>
        <?php endfor; ?>
    </p>
<?php endif; ?>

<?php admin_footer(); ?>
