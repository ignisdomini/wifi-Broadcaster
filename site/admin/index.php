<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/bootstrap.php';
efir_require_installed();
require_once __DIR__ . '/_layout.php';

$error = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST' && !admin_is_logged_in()) {
    csrf_check();
    if (admin_login((string)($_POST['password'] ?? ''))) {
        header('Location: /admin/');
        exit;
    }
    $error = 'Неверный пароль.';
}

if (!admin_is_logged_in()) {
    $siteName = (string)setting('site_name');
    ?>
    <!doctype html>
    <html lang="ru">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Вход — админка <?= e($siteName) ?></title>
        <link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
        <link rel="stylesheet" href="/assets/style.css">
    </head>
    <body class="page-plain">
    <div class="install">
        <span class="brand-mark"><?= e($siteName) ?></span>
        <p class="muted">Штаб · вход</p>
        <?php if ($error !== null): ?>
            <div class="notice notice-error"><?= e($error) ?></div>
        <?php endif; ?>
        <form method="post" class="form">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <label>Пароль<input type="password" name="password" required autofocus></label>
            <button class="btn" type="submit">Войти</button>
        </form>
        <p style="margin-top:24px"><a href="/">← На главную</a></p>
    </div>
    </body>
    </html>
    <?php
    exit;
}

// --- Сводка -------------------------------------------------------------

$totalVisible = count_posts(true);
$totalAll = count_posts(false);
$hidden = $totalAll - $totalVisible;

$last24 = db_one(
    'SELECT COUNT(*) AS c FROM posts WHERE created_at > DATE_SUB(NOW(), INTERVAL 1 DAY)'
);

$usersRow = db_one('SELECT COUNT(*) AS c FROM users');
$usersCount = (int)($usersRow['c'] ?? 0);

$recent = db_all('SELECT * FROM posts ORDER BY id DESC LIMIT 10');


admin_header('Сводка');
?>

<div class="stat-row">
    <div class="stat"><b><?= $totalVisible ?></b><span>в эфире</span></div>
    <div class="stat"><b><?= $hidden ?></b><span>скрыто</span></div>
    <div class="stat"><b><?= (int)($last24['c'] ?? 0) ?></b><span>за сутки</span></div>
    <div class="stat"><b><?= $usersCount ?></b><span>позывных</span></div>
</div>

<?php if (is_file(EFIR_ROOT . '/install.php')): ?>
    <div class="notice notice-warn">
        На сервере остался файл <code>install.php</code>. Удалите его — через него можно
        переустановить сайт, если кто-то удалит <code>config.php</code>.
    </div>
<?php endif; ?>

<h2>Последние записи</h2>
<table class="rows">
    <tr>
        <th>код</th><th>от кого</th><th>в эфир</th><th>когда</th><th></th>
    </tr>
    <?php foreach ($recent as $row): ?>
        <tr class="<?= (int)$row['is_hidden'] === 1 ? 'hidden-row' : '' ?>">
            <td><a href="/p/<?= e((string)$row['code']) ?>"><?= e((string)$row['code']) ?></a></td>
            <td><?= e((string)$row['nick'] !== '' ? (string)$row['nick'] : 'аноним') ?></td>
            <td><?= e(mb_substr((string)$row['broadcast'], 0, 60)) ?></td>
            <td><?= e(human_time((string)$row['created_at'])) ?></td>
            <td><?= (int)$row['is_hidden'] === 1 ? 'скрыто' : '' ?></td>
        </tr>
    <?php endforeach; ?>
    <?php if (!$recent): ?>
        <tr><td colspan="5" class="muted">Пока ничего не приходило.</td></tr>
    <?php endif; ?>
</table>

<p style="margin-top:18px"><a href="/admin/posts.php">Все передачи →</a></p>

<?php admin_footer(); ?>
