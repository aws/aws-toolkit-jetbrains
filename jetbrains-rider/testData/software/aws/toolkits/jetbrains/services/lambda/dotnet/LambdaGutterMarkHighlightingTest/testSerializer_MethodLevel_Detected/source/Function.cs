using Amazon.Lambda.Core;

namespace HelloWorld
{
    public class Function
    {
        [LambdaSerializer(typeof(Amazon.Lambda.Serialization.Json.JsonSerializer))]
        public Product DescribeProduct(DescribeProductRequest request)
        {
            var catalogService = new CatalogService();
            return catalogService.DescribeProduct(request.Id);
        }
    }

    public class DescribeProductRequest
    {
        public int Id { get; set; }
    }

    public class Product
    {
        public int Id { get; set; }
    }

    public class CatalogService
    {
        public Product DescribeProduct(int id)
        {
            return new Product { Id = id };
        }
    }
}