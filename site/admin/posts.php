<?php
declare(strict_types=1);

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
            case 'delete':
                delete_post($id);
                $notice = 'Запись удалена.';
                break;
            case 'hide':
                db_exec('UPDATE posts SET is_hidden = 1 WHERE id = ?', [$id]);
                $notice = 'Передача снята с эфира.';
                break;
            case 'show':
                db_exec('UPDATE posts SET is_hidden = 0 WHERE id = ?', [$id]);
                $notice = 'Передача возвращена в эфир.';
                break;
        }
    }
}

$perPage = 40;
$page = max(1, (int)($_GET['page'] ?? 1));
$offset = ($page - 1) * $perPage;

$total = count_posts(false);
$pages = max(1, (int)ceil($total / $perPage));

$rows = db_all(
    'SELECT * FROM posts ORDER BY id DESC LIMIT ' . $perPage . ' OFFSET ' . $offset
);

admin_header('Передачи', true);
?>

<?php if ($notice !== null): ?>
    <div class="notice notice-ok"><?= e($notice) ?></div>
<?php endif; ?>

<p class="muted">
    Всего <?= $total ?> <?= plural($total, 'передача', 'передачи', 'передач') ?>,
    страница <?= $page ?> из <?= $pages ?>.
</p>

<table class="rows">
    <tr>
        <th>код</th>
        <th>от кого</th>
        <th>в эфир ушло</th>
        <th>текст</th>
        <th>просм.</th>
        <th>когда</th>
        <th>действия</th>
    </tr>
    <?php foreach ($rows as $row): ?>
        <tr class="<?= (int)$row['is_hidden'] === 1 ? 'hidden-row' : '' ?>">
            <td><a href="/p/<?= e((string)$row['code']) ?>" target="_blank"><?= e((string)$row['code']) ?></a></td>
            <td><?= e((string)$row['nick'] !== '' ? (string)$row['nick'] : 'аноним') ?></td>
            <td><?= e(mb_substr((string)$row['broadcast'], 0, 70)) ?></td>
            <td><?= e(mb_substr(trim((string)$row['body']), 0, 70)) ?><?= mb_strlen((string)$row['body']) > 70 ? '…' : '' ?></td>
            <td><?= (int)$row['views'] ?></td>
            <td><?= e(date('d.m.y H:i', strtotime((string)$row['created_at']))) ?></td>
            <td>
                <form method="post" class="inline-form">
                    <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
                    <input type="hidden" name="id" value="<?= (int)$row['id'] ?>">
                    <?php if ((int)$row['is_hidden'] === 1): ?>
                        <button class="btn btn-ghost" name="action" value="show" type="submit">в эфир</button>
                    <?php else: ?>
                        <button class="btn btn-ghost" name="action" value="hide" type="submit">снять</button>
                    <?php endif; ?>
                </form>
                <form method="post" class="inline-form"
                      onsubmit="return confirm('Удалить запись <?= e((string)$row['code']) ?>?');">
                    <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
                    <input type="hidden" name="id" value="<?= (int)$row['id'] ?>">
                    <button class="btn btn-danger" name="action" value="delete" type="submit">удалить</button>
                </form>
            </td>
        </tr>
    <?php endforeach; ?>
    <?php if (!$rows): ?>
        <tr><td colspan="7" class="muted">Пусто.</td></tr>
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
