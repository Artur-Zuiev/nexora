import { expect, test, type Page } from '@playwright/test';

const adminPassword = process.env.E2E_ADMIN_PASSWORD;
if (!adminPassword) {
  throw new Error('E2E_ADMIN_PASSWORD is required. Use a disposable local test account only.');
}

async function signIn(page: Page, username = 'admin', password = adminPassword!) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Business overview' })).toBeVisible();
}

test('admin session persists on reload and logout revokes access', async ({ page }) => {
  await signIn(page);
  await expect(page.getByText('Administrator', { exact: false }).first()).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Business overview' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
});

test('admin adds and searches a disposable CRM lead', async ({ page }) => {
  await signIn(page);
  const unique = Date.now().toString(36);
  const leadName = `E2E Demo ${unique}`;
  const leadEmail = `e2e-${unique}@example.test`;
  // Use the labelled controls; no real customer information is used.
  await page.getByRole('textbox', { name: 'Customer name' }).fill(leadName);
  await page.getByRole('textbox', { name: 'Email', exact: true }).fill(leadEmail);
  await page.getByRole('button', { name: 'Add lead' }).click();
  const pipeline = page.locator('section').filter({
    has: page.getByRole('heading', { name: 'Lead pipeline' })
  });
  await expect(pipeline.getByRole('row', { name: new RegExp(leadName) })).toBeVisible();
  await page.getByRole('searchbox', { name: 'Search leads' }).fill(leadEmail);
  await expect(pipeline.getByRole('row', { name: new RegExp(leadName) })).toBeVisible();
  await expect(pipeline.getByRole('row')).toHaveCount(2); // header + one matching lead
});

test('new member can read CRM but cannot edit or manage team', async ({ page }) => {
  await signIn(page);
  const unique = Date.now().toString(36);
  const username = `e2e-member-${unique}`;
  const password = `e2e-Unique-Pass-${unique}-123`;
  await page.getByRole('textbox', { name: 'New member username' }).fill(username);
  await page.getByLabel('Temporary password (12+ characters)').fill(password);
  await page.getByRole('button', { name: 'Create member' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Member created' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();

  await signIn(page, username, password);
  await expect(page.getByRole('heading', { name: 'Lead pipeline' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Add a lead' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Team members' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
});

test('sidebar navigates to real workspace sections', async ({ page }) => {
  await signIn(page);
  await page.getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name: 'Lead pipeline' }).click();
  await expect(page).toHaveURL(/#pipeline$/);
  await expect(page.locator('#pipeline').getByRole('heading', { name: 'Lead pipeline' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name: 'Account security' }).click();
  await expect(page).toHaveURL(/#account-security$/);
  await expect(page.locator('#account-security').getByRole('heading', { name: 'Account security' })).toBeVisible();
});

test('mobile navigation remains accessible and functional', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page);
  const navigation = page.getByRole('navigation', { name: 'Main navigation' });
  await expect(navigation.getByRole('link', { name: 'Follow-ups' })).toBeVisible();
  await navigation.getByRole('link', { name: 'Follow-ups' }).click();
  await expect(page).toHaveURL(/#tasks$/);
  await expect(page.locator('#tasks').getByRole('heading', { name: 'Follow-up tasks' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
});

test('follow-up priorities, task search and customer navigation', async ({ page }) => {
  await signIn(page);
  const unique = Date.now().toString(36);
  const customer = `Task Customer ${unique}`;
  const title = `Call customer ${unique}`;
  await page.getByRole('textbox', { name: 'Customer name' }).fill(customer);
  await page.getByRole('textbox', { name: 'Email', exact: true }).fill(`task-${unique}@example.test`);
  await page.getByRole('button', { name: 'Add lead' }).click();
  await expect(page.locator('#lead-detail')).toBeVisible();
  await page.getByRole('textbox', { name: 'Task', exact: true }).fill(title);
  const today = await page.evaluate(() => {
    const date = new Date();
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  });
  await page.getByLabel('Due date').fill(today);
  await page.getByRole('button', { name: 'Add task' }).click();

  const taskPanel = page.locator('#tasks');
  await taskPanel.getByRole('button', { name: /Due today/ }).click();
  await expect(taskPanel.getByRole('row', { name: new RegExp(title) })).toBeVisible();

  await taskPanel.getByRole('searchbox', { name: 'Search follow-ups' }).fill('not-a-match');
  await expect(taskPanel.getByText('No tasks in this view')).toBeVisible();
  await taskPanel.getByRole('button', { name: 'Reset task filters' }).click();
  await expect(taskPanel.getByRole('row', { name: new RegExp(title) })).toBeVisible();

  await taskPanel.getByRole('button', { name: customer }).click();
  await expect(page.locator('#lead-detail').getByRole('heading', { name: customer })).toBeVisible();
  await taskPanel.getByRole('row', { name: new RegExp(title) })
    .getByRole('button', { name: 'Mark done' }).click();
  await taskPanel.getByRole('combobox', { name: 'Show tasks' }).selectOption('DONE');
  await expect(taskPanel.getByRole('row', { name: new RegExp(title) })).toBeVisible();
});

test('admin downloads tenant-scoped CSV data from the CRM', async ({ page }) => {
  await signIn(page);
  const leadDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export leads CSV' }).click();
  const leadsFile = await leadDownload;
  expect(leadsFile.suggestedFilename()).toBe('nexora-leads.csv');

  const taskDownload = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export tasks CSV' }).click();
  const tasksFile = await taskDownload;
  expect(tasksFile.suggestedFilename()).toBe('nexora-tasks.csv');
});

test('members cannot see bulk CSV export controls', async ({ page }) => {
  await signIn(page);
  const unique = Date.now().toString(36);
  const username = `csv-member-${unique}`;
  const password = `csv-member-secret-${unique}-123`;
  await page.getByRole('textbox', { name: 'New member username' }).fill(username);
  await page.getByLabel('Temporary password (12+ characters)').fill(password);
  await page.getByRole('button', { name: 'Create member' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Member created' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await signIn(page, username, password);
  await expect(page.getByRole('button', { name: 'Export leads CSV' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Export tasks CSV' })).toHaveCount(0);
});
