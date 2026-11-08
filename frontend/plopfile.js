// Generators follow CLAUDE.md: kebab-case files/folders, PascalCase components, feature-based layout.
const FEATURE_DIRS = ['api', 'components', 'hooks', 'stores', 'types', 'utils'];

export default function (plop) {
  plop.setGenerator('feature', {
    description: 'Create a self-contained feature in src/features',
    prompts: [{ type: 'input', name: 'name', message: 'Feature name:' }],
    actions: FEATURE_DIRS.map((dir) => ({
      type: 'add',
      path: `src/features/{{kebabCase name}}/${dir}/.gitkeep`,
      template: '',
    })),
  });

  plop.setGenerator('component', {
    description: 'Create a component (shared or inside a feature)',
    prompts: [
      { type: 'input', name: 'name', message: 'Component name:' },
      {
        type: 'input',
        name: 'feature',
        message: 'Feature name (leave empty for shared src/components):',
      },
    ],
    actions: (data) => {
      const base = data.feature
        ? 'src/features/{{kebabCase feature}}/components'
        : 'src/components';
      return [
        {
          type: 'add',
          path: `${base}/{{kebabCase name}}/index.tsx`,
          templateFile: 'plop-templates/component.hbs',
        },
        {
          type: 'add',
          path: `${base}/{{kebabCase name}}/{{kebabCase name}}.module.css`,
          templateFile: 'plop-templates/component.module.css.hbs',
        },
      ];
    },
  });
}
