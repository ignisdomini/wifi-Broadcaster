<?php
declare(strict_types=1);

/**
 * Отдельная страница для текстов главной.
 *
 * Держать их вместе с лимитами и паролем было бы неудобно: полей два десятка,
 * и правят их по другому поводу. Список полей един для формы и для приёма
 * данных — см. content_fields() в lib/content.php.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';
efir_require_installed();
require_once __DIR__ . '/_layout.php';
admin_require();

$saved = false;
$restored = false;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();

    if (isset($_POST['restore'])) {
        // Возврат к исходным текстам: полезно, если правкой всё запутали.
        $defaults = setting_defaults();
        $values = [];
        foreach (content_fields() as $group) {
            foreach (array_keys($group['fields']) as $key) {
                $values[$key] = (string)($defaults[$key] ?? '');
            }
        }
        save_settings($values);
        $restored = true;
    } else {
        $values = [];
        foreach (content_fields() as $group) {
            foreach (array_keys($group['fields']) as $key) {
                if (array_key_exists($key, $_POST)) {
                    $values[$key] = trim((string)$_POST[$key]);
                }
            }
        }
        save_settings($values);
        $saved = true;
    }
}

$s = all_settings(true);
admin_header('Содержимое');
?>

<?php if ($saved): ?>
    <div class="notice notice-ok">Сохранено. Откройте <a href="/" target="_blank">главную</a>, чтобы посмотреть.</div>
<?php endif; ?>
<?php if ($restored): ?>
    <div class="notice notice-ok">Исходные тексты возвращены.</div>
<?php endif; ?>

<div class="notice">
    <p class="muted">
        Здесь правится всё, что видно на главной странице: заголовок, разделы,
        карточки и подписи. Пустой заголовок карточки убирает её со страницы
        целиком — так лишние блоки не приходится комментировать в коде.
    </p>
</div>

<form method="post" class="form">
    <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">

    <?php foreach (content_fields() as $group): ?>
        <h2><?= e($group['title']) ?></h2>
        <?php foreach ($group['fields'] as $key => $label): ?>
            <label>
                <?= e($label) ?>
                <?php if (in_array($key, content_long_fields(), true)): ?>
                    <textarea name="<?= e($key) ?>"><?= e((string)($s[$key] ?? '')) ?></textarea>
                <?php else: ?>
                    <input type="text" name="<?= e($key) ?>" value="<?= e((string)($s[$key] ?? '')) ?>">
                <?php endif; ?>
            </label>
        <?php endforeach; ?>
    <?php endforeach; ?>

    <div style="display:flex;gap:1rem;flex-wrap:wrap;margin-top:1rem">
        <button class="btn" type="submit">Сохранить</button>
        <button class="btn btn-ghost" type="submit" name="restore" value="1"
                onclick="return confirm('Вернуть исходные тексты? Ваши правки будут потеряны.');">
            Вернуть исходные
        </button>
    </div>
</form>

<?php admin_footer(); ?>
